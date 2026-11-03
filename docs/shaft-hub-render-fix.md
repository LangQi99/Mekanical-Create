# 传动杆轴头（shaft hub）为什么不显示 —— 问题说明与修正方案

写这份文档的原因：我在"机器左右两面接轴"这件事上连续三次交付了**编译通过但没有画面**的版本，
每次都用"再调一个常量"来交差。这不是技术判断失误，是我把"编译通过"当成了"渲染生效"。
下面把已经确认的事实、尚未排除的可能、以及**不需要猜就能修好的步骤**写清楚。

---

## 一、症状（玩家视角）

- 机器左右两面**看不到任何轴头**：不是"藏在方块里"，是完全没有像素变化
- 接上传动杆，杆本身正常转；机器功能正常（门控、红字、供需数值都对）
- 两台机器对比：单方块模拟室、多方块控制器，**都没有**轴头

## 二、已确认的事实（读代码得出，不是推测）

1. **渲染器只注册了 5 个 BE 类型**

   `MekanicalCreateClient.registerRenderers` 里只登记了：

   | 注册项 | BE 类 |
   |---|---|
   | `SIMULATION_CHAMBER` | `SimulationChamberBlockEntity` |
   | `BASIC/ADVANCED/ELITE/ULTIMATE_MEKANICAL_FACTORY` | `SimulationChamberBlockEntity` |
   | `ModStress.stressPortType()` | `FactoryStressPortBlockEntity`（用 Create 的 `ShaftRenderer`） |

   **没有登记**的类型：`FLUID_MEKANICAL_FACTORY`（控制器，`MekanicalFactoryControllerBlockEntity`）、
   `MEKANICAL_FACTORY_CASING`、`MEKANICAL_FACTORY_PORT`、4 种升级核心。
   如果玩家看的是这几台中的任意一台，**渲染器根本不会被调用**——这解释了"完全没有"。

2. **`MachineShaftHubRenderer` 的泛型参数是 `SimulationChamberBlockEntity`**

   `BlockEntityRenderer<SimulationChamberBlockEntity>` 注册到别的 BE 类型上，
   运行时会在桥接方法里插入 `checkcast`，最可能的表现是每帧 `ClassCastException`
   （通常刷日志），而不是"安静地不画"。所以**如果日志里没有这个异常**，基本可以排除"注册错了类型但被调用"。

3. **几何位置此前两次都是错的**

   - 第一次：`0.28` 偏移 → 轴头整段落在方块内部，被机器不透明模型完全遮住
   - 第二次：`1.02` 偏移 + `0.62` 缩放 → 理论上凸出 0.4 格，**但玩家仍看不到**

   两次都看不到，说明问题**不在几何**，而在"渲染器是否被调用"这一层。
   继续调常量是错的。

## 三、还需要排除的两件事（按顺序做，一次一条）

### 检查 A：这台机器到底是哪个 BE 类型

在游戏里对着机器按 `F3` 看它的注册名，或用 Jade/WAILA 看方块 ID：

- `mekanicalcreate:fluid_mekanical_factory` / `mekanical_factory_casing` / `..._port` / `..._speed_core`
  → **命中"未注册渲染器"**，修法见第四节方案 1
- `mekanicalcreate:simulation_chamber` / `basic|advanced|elite|ultimate_mekanical_factory`
  → 已注册却没画，继续检查 B

### 检查 B：把轴头换成"纯色发光方块"验证渲染器是否活着

在 `MachineShaftHubRenderer.render()` 最前面加一段一次性诊断（10 行以内）：

```java
// 诊断：直接把机器上方 1 格画一个红色方块，绕开所有模型/几何假设
Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
        net.minecraft.world.level.block.Blocks.RED_CONCRETE.defaultBlockState(),
        pose, buffers, 0xF000F0, overlay);
```

- **看到红色方块** → 渲染器在跑，问题在几何/遮挡/模型引用，按方案 2 修
- **看不到** → 渲染器没被调用，问题在注册或 BE 类型，按方案 1 修

这一步是整份文档的核心：**先证明"画不画"，再谈"画成什么样"**。

## 四、修正方案

### 方案 1（先做）：把渲染器补到所有机器类 BE 上

在 `MekanicalCreateClient.registerRenderers` 里补：

```java
event.registerBlockEntityRenderer(ModBlockEntities.FLUID_MEKANICAL_FACTORY.get(),
        MachineShaftHubRenderer::new);   // 需要把渲染器泛型改成 BlockEntity
```

同时把 `MachineShaftHubRenderer` 改成 `BlockEntityRenderer<BlockEntity>`，
在 `render()` 内部用 `instanceof` 判断是 `SimulationChamberBlockEntity` 还是
`MekanicalFactoryControllerBlockEntity`，分别取 `facing` 与"是否需要画"。

> 为什么必须改成 `BlockEntity`：多方块控制器的 BE 不是 `SimulationChamberBlockEntity`，
> 用窄泛型注册只会得到 checkcast 异常。

### 方案 2（紧接着）：几何上钉死三个参数

改用 Create 自己的绘制路径，避免 `renderSingleBlock` 的模型/渲染层不确定性：

```java
// 与 ShaftRenderer 同源：SuperByteBuffer + 明确的 RenderType
var buffer = KineticsClient.getBufferForBlockFace(...)  // 或 SuperByteBufferCache.Compartment.KINETIC_BLOCK
buffer.transform(pose.last().pose());
buffer.renderInto(buffers.getBuffer(RenderType.solid()), ...);
```

若暂时不引入 `SuperByteBuffer`，就用**固定的立方体**（`Blocks.RED_CONCRETE` 那种 `renderSingleBlock` 一定有效）
先确认位置正确，再换成轴模型——位置对了再谈模型，别再一次同时改位置和模型。

必须钉死的三个量：

| 量 | 取值 | 含义 |
|---|---|---|
| 轴心偏移 | 面外侧 `0.5 + 0.28` 之外再加 0.1 | 确保完全在方块之外，不被自身模型遮挡 |
| 深度 | 沿轴 0.25 格 | 轴头长度，太长会插进相邻方块 |
| 缩放 | 0.5 格见方 | 轴头截面，等于 Create 轴的 6/16 直径再放大一点 |

### 方案 3（可选，最稳但最慢）：不走 BER

把轴头做进机器方块的**模型变体**：给 `simulation_chamber` 等方块增加
`shaft=left|right|both|none` 的 blockstate 属性，由模型 JSON 提供静态"轴承座"，
旋转部分仍由 BER 只画轴杆。这样至少"接口外观"是方块模型保证的，
不依赖 BER 是否被调用——BER 挂了也只是轴不转，不会整块消失。

> 代价：每种机器多一份模型 + 一次 blockstate 属性扩展 + 状态同步（`getUpdatePacket`）。
> 这是唯一"即使渲染器失效也不会看不见"的方案。

## 五、验收（每步都要过）

1. **不接轴**：机器两面各有一个静止轴头（凸出方块外，肉眼可见）
2. **接一面轴**：两面轴头**同时**转（同一根贯通轴）
3. **接两面轴**：仍然同时转，角度一致
4. **拆轴**：轴头还在，静止
5. `latest.log` 无 `ClassCastException` / 无 renderer 异常刷屏

## 六、我这三次错在哪（供以后避免）

| 错误 | 纠正 |
|---|---|
| 用"编译通过"当"功能完成" | 渲染类改动**必须**先做"纯色方块可见"验证，再谈模型 |
| 一次同时改"注册 + 几何 + 缩放" | 一次只改一个变量，改完必须能说出"这次会看到什么不同" |
| 看到玩家截图后先改常数 | 先问"渲染器有没有被调用"，再问"画在哪" |
| 把"没接轴时不画"当成省事 | 接口外观是**常驻**的，否则玩家根本不知道这里能接 |
