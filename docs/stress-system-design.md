# 0.3.0 应力系统 — 设计定稿（play-test 后修订）

修订原因：第一版把"应力接口"做成了一台机器旁边额外放的方块，玩家反馈"这不是原生，
我要轴直接插在机器侧面"。本文件是修订后的唯一设计依据。

## 一、硬约束（决定了设计）

- Create 的应力账本只认 `KineticBlockEntity`：`KineticNetwork.calculateStress()` 就是
  把 `members`（`Map<KineticBlockEntity, Float>`）里的值累加。
- Mekanism 机器的 BE 是 `TileEntityMekanism`，Java 单继承，**同一坐标不可能同时是
  Create 动能块**。
- 因此"机器自己进网络"不可能；能做到"轴直接贴机器侧面"的方式是：
  **机器方块实现 `IRotate`（只对左右两面 `hasShaftTowards`）+ 机器把应力需求累加到
  相邻那根轴的网络应力里**。

## 二、最终形态

### 1) 单方块机器（simulation_chamber + basic/advanced/elite/ultimate factory）

- 机器方块实现 `IRotate`：`hasShaftTowards` 只对机器的**左/右**两面返回 true
  （左右按机器 `facing` 定义，随扳手旋转而转）。
- `getRotationAxis` = 左右方向所在水平轴（X 或 Z）。
- 机器 BE 每 tick：
  - 扫描左右两个相邻方块，找 `KineticBlockEntity`（轴、齿轮、同轴机器都算）；
  - 有效轴数 N；每根轴承担 `需求/N`；
  - 通过 `network.updateStressFor(shaft, shaftBaseStress + 本机份额)` 把需求压进网络；
  - 判定"有轴且 `shaft.getSpeed() != 0`"才算有动力（超载时 Create 会把整网转速置 0）；
  - 不满足 → lane 全部 `STRESS_STARVED`，不抽能量、进度冻结。
- 多机共享一根轴：用静态聚合表（`Map<轴BE, Long>`）做累加，每 tick 重算后统一 push，
  避免互相覆盖。轴被拆时清理表项。

### 2) 多方块工厂（fluid_mekanical_factory）

- 新增方块 **`mekanical_factory_stress_port`（通用动力工厂应力接口）**：
  - 结构方块：`MekanicalFactoryValidator.getCasingType` 认成 `CasingType.VALVE`，
    `postcheck` 记录端口坐标；
  - BE 是真正的 `KineticBlockEntity`（它自己申报 SU），所以多方块这边是"真消费"；
  - 带旋转轴渲染：注册 `ShaftRenderer<...>`（同第一版）；
  - 贴图参考现有 `mekanical_factory_port`（端口）的材质风格，模型做成薄板+法兰。
- 需求 = `结构体积 × 256 SU × (1 + 速度核心数)`（已实现在
  `MekanicalFactoryMultiblockData.requiredStressSu()`）。
- 端口被拆/未装 → 多方块 `isStressSatisfied()` 为 false（当前临时返回 true，待接）。

### 3) 删除项

- 第一版的 `stress_inlet` 方块与物品：**删除**（含 blockstate/model/recipe/loot/tag/lang）。
  机器不再需要额外方块。

## 三、提示（保持上一轮结论）

- 一句红字「显然，应力不足以支撑该机器运转」+ 第二行「需要 X SU / 已接入 Y SU」。
- 状态分两种但共用一句话；数值行区分"没接轴（供给 0）"与"接了但不够"。

## 四、待办清单（按实现顺序）

1. `BlockMekanicalMachine extends BlockTile ... implements IRotate`（4 个工厂 + 模拟室共 5 个
   方块改用这个类；fluid factory 仍是 `BlockBasicMultiblock`，不改）。
2. `SimulationChamberBlockEntity`：左右找轴、聚合应力表、门控（已有 stressStarved 骨架）。
3. 新增 `mekanical_factory_stress_port` 方块 + BE + 资源 + validator 认 VALVE + 记录坐标。
4. 多方块数据接端口状态（`isStressSatisfied` 真实化）。
5. 删 `stress_inlet`。
6. 构建 + 冒烟 + 游戏内验证（本次必须由我先自测过再交付）。


## 五、Jade 实现规格（下一轮直接照做）

编译期依赖：把 `D:\.minecraft\versions\Project antrotopy\mods\Jade-1.21.1-NeoForge-15.10.6.jar`
复制到 `libs/`，并在 `tools/build.js` 的依赖清单里加一项（与 create/mekanism/jei 同级），
否则 javac 找不到 `snownee.jade.*`。

新增 `client/jade/MekanicalJadePlugin.java`：

```java
@WailaPlugin(MekanicalCreate.MOD_ID)   // Jade 靠注解扫描发现插件
public class MekanicalJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineStressProvider.INSTANCE, BlockMekanicalMachine.class);
        registration.registerBlockComponent(StressPortProvider.INSTANCE, FactoryStressPortBlock.class);
    }
}
```

`MachineStressProvider implements IBlockComponentProvider`：
- `getUid()` 返回自定义 `ResourceLocation`
- `appendTooltip(ITooltip tooltip, IBlockAccessor access, IPluginConfig config)`：
  - `access.getBlockEntity() instanceof SimulationChamberBlockEntity m`
  - 若 `m.isStressStarved()` → 加一行红字 `gui.mekanicalcreate.stress_insufficient`
  - 再加一行 `gui.mekanicalcreate.stress_numbers`（`m.clientStressDemandSu()`, `m.clientStressSupplySu()`）——
    这两个值已经通过 `SyncableLong` 同步到客户端，Jade 直接读即可，**不需要额外 NBT 同步**

`StressPortProvider`：`FactoryStressPortBlockEntity port` → 显示
`需要 X SU`（`port.requiredSu()`）+ `RPM: ${Math.abs(port.getSpeed())}`

注意：Jade 的 `getSpeed()` 在客户端依赖 `KineticBlockEntity` 的网络同步；端口 BE 是原生 kinetic，
所以 RPM 可直接读。若显示为 0，改用 `access.getServerData()`（需要注册 common 侧 provider）。

验证：`/reload` 不够，必须重启客户端；看 `latest.log` 是否有 Jade 插件加载失败堆栈。
