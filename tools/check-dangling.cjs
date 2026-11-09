// 从交接文档 §6.1 改编：检查 jar 里所有 tags / recipe / loot_table / blockstate 引用的
// mekanicalcreate:* 是否真的出现在 class 字节里（即是否被注册）。
const fs = require('fs');
const zlib = require('zlib');

const jar = process.argv[2];
if (!jar) { console.error('用法: node check-dangling.cjs <jar>'); process.exit(2); }
const buf = fs.readFileSync(jar);

let q = 0;
const classes = [];
const jsons = [];
while ((q = buf.indexOf(Buffer.from([0x50, 0x4b, 0x01, 0x02]), q)) !== -1) {
  const method = buf.readUInt16LE(q + 10), cs = buf.readUInt32LE(q + 20);
  const nl = buf.readUInt16LE(q + 28), el = buf.readUInt16LE(q + 30);
  const name = buf.subarray(q + 46, q + 46 + nl).toString('utf8');
  const lho = buf.readUInt32LE(q + 42);
  const lnl = buf.readUInt16LE(lho + 26), lel = buf.readUInt16LE(lho + 28);
  const ds = lho + 30 + lnl + lel;
  try {
    const raw = buf.subarray(ds, ds + cs);
    const data = method === 8 ? zlib.inflateRawSync(raw) : raw;
    if (name.endsWith('.class')) classes.push(data.toString('latin1'));
    else if (name.endsWith('.json')) jsons.push({ name, body: data.toString('utf8') });
  } catch {}
  q += 46 + nl + el;
}

const blob = classes.join('\u0000');
const registered = (id) => blob.includes(id.split(':')[1]);
const problems = [];

for (const j of jsons) {
  if (!/^data\/.*\/tags\//.test(j.name)) continue;
  let o; try { o = JSON.parse(j.body); } catch { continue; }
  for (const v of o.values ?? []) {
    const id = typeof v === 'string' ? v : v.id;
    if (!id || !id.includes(':')) continue;
    if (id.split(':')[0] !== 'mekanicalcreate') continue;
    if (!registered(id)) problems.push(['TAG', j.name, id]);
  }
}

for (const j of jsons) {
  if (!/^data\/mekanicalcreate\/(recipe|loot_table|advancement)\//.test(j.name)) continue;
  for (const m of j.body.matchAll(/"mekanicalcreate:([a-z0-9_]+)"/g)) {
    if (!registered('mekanicalcreate:' + m[1])) problems.push(['DATA', j.name, 'mekanicalcreate:' + m[1]]);
  }
}

for (const j of jsons) {
  const m = j.name.match(/^assets\/mekanicalcreate\/blockstates\/([a-z0-9_]+)\.json$/);
  if (m && !registered('mekanicalcreate:' + m[1])) problems.push(['ASSET', j.name, m[1]]);
}

console.log('scanned jsons:', jsons.length, 'classes:', classes.length);
if (problems.length) {
  console.error('❌ 悬空引用 ' + problems.length + ' 处：');
  for (const [k, f, id] of problems) console.error('   [' + k + '] ' + f + ' -> ' + id);
  process.exit(1);
}
console.log('✅ 无悬空引用');
