<div align="center">

# Mekanical Create · 通用动力

**把机械动力的物理产线，装进 Mekanism 风格的通用工厂。**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge)](#运行环境)
[![Forge](https://img.shields.io/badge/Forge-47.x-EF6C61?style=for-the-badge)](#运行环境)
[![Release](https://img.shields.io/github/v/release/LangQi99/Mekanical-Create?style=for-the-badge&label=Release)](https://github.com/LangQi99/Mekanical-Create/releases/latest)
[![License](https://img.shields.io/github/license/LangQi99/Mekanical-Create?style=for-the-badge)](LICENSE)

<img src="docs/img/community-feedback.jpg" alt="玩家对通用动力的反馈" width="520">

<sub>“万物皆可 Mek”——来自玩家的真实反馈</sub>

</div>

---

## 这是什么？

**Mekanical Create（通用动力）** 是一个连接 [Create（机械动力）](https://github.com/Creators-of-Create/Create) 与 [Mekanism（通用机械）](https://github.com/mekanism/Mekanism) 的 Forge 附属模组。

> 当前分支面向 **Minecraft 1.20.1 + Forge**；Minecraft 1.21.1 + NeoForge 版本请查看 `main` 分支。

它提供一套 Mekanism 风格的 **通用动力工厂**：把机械手、动力锯、动力冲压机、鼓风机等机械动力设备作为“加工模块”放入机器，即可使用统一的输入、输出、供能、升级和侧面配置系统完成对应配方。

> 不再为每种 Create 配方单独搭建一条产线，同时仍然读取 Create 的原始配方与序列组装规则。

## 界面预览

<div align="center">

<img src="docs/img/factory-interface.png" alt="精英通用动力工厂界面" width="760">

<sub>精英通用动力工厂：16 格无序输入、4 格缓冲输出、独立能量槽与完整 Mekanism 侧面配置</sub>

</div>

## 核心功能

| 功能 | 说明 |
| --- | --- |
| 模块化加工 | 放入不同 Create 机器模块，自动启用相应配方目录 |
| 无序输入 | 材料只要放进 16 个输入槽即可，无需按合成形状或步骤排序 |
| 智能选方 | 多个配方同时匹配时，优先选择单次消耗材料更多、步骤更完整的配方 |
| 单次加工 | 每走完一次进度条，只执行一次配方，不会瞬间清空全部材料 |
| Mekanism 体系 | 原生 Mek 风格 GUI、FE 能量、升级、自动弹出和六面输入输出配置 |
| JEI 联动 | 工厂配方、催化剂、概率输出及序列组装信息均可在 JEI 中查看 |
| 机器音效 | 加工时播放 Mekanism 机器运作声，并支持消音升级 |

### 支持的 Create 加工模块

- **机械手**：普通机械手加工、物品应用，以及展开后的序列组装。
- **动力冲压机**：压片与冲压配方。
- **动力锯**：切割配方。
- **鼓风机**：搭配水、熔岩、灵魂篝火或普通篝火标记执行对应加工。
- **石磨与粉碎轮**：研磨、粉碎及概率副产物。
- **动力合成器**：原版工作台配方与 Create 动力合成配方。

物品应用配方也会按照 Create 自己的转换逻辑交给机械手执行，例如：

```text
去皮原木 + 安山合金 → 安山机壳
```

## 输入与配方选择

工厂把输入槽看作一个无序材料池。标签材料、同类替代品和分散在多个槽位中的同种物品都可以参与匹配。

当材料同时符合多个配方时，机器优先选择 **单次需求量更大的配方**。例如同时存在“2 个石头 → A”和“4 个石头 → B”，输入 8 个石头时会优先选择后者；一次进度只消耗 4 个石头并产出一次 B，剩余材料等待下一轮加工。

模块、鼓风条件和配方中声明为“不消耗”的工具只作为催化剂，不会被普通材料消耗逻辑扣除。

## 序列组装

<div align="center">

<img src="docs/img/jei-sequenced-assembly.png" alt="通用动力工厂的 JEI 序列组装配方" width="820">

<sub>JEI 会显示展开后的步骤数、循环次数、总材料用量和加权概率输出</sub>

</div>

Create 的序列组装会被展开成工厂中的一次完整操作：

- 起始物品消耗 1 次。
- 机械手耗材按照循环次数自动合并并计算总量。
- 不消耗的工具保持为催化剂。
- 最终结果按照 Create 原配方的权重池抽取一次。
- 不会生成或暴露中间态的序列组装物品。

## 工厂等级

工厂可以使用 Mekanism 的工厂安装器逐级升级，升级时保留库存、能量、升级组件和侧面配置。

| 等级 | 灯带颜色 | 特点 |
| --- | --- | --- |
| 初始级 | 黄色 | 最低级通用动力工厂 |
| 基础级 | 绿色 | 基础 Mekanism 工厂等级 |
| 高级 | 红色 | 更高速度与能量容量 |
| 精英 | 青色 | 高性能工厂等级 |
| 终极 | 紫色 | 当前最高工厂等级 |

等级越高，加工速度、能耗与能量容量越高。速度、能量与消音升级均使用 Mekanism 原生升级系统。

## 运行环境

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.1.33 或更高的 47.x 版本 |
| Create | 6.0.8 |
| Mekanism | 10.4.16.80 |
| JEI | 15.20.x（可选，但推荐安装） |
| Java | 17 |

## 安装

1. 安装 Minecraft 1.20.1 与 Forge 47.x。
2. 安装 Create、Mekanism 和 JEI。
3. 从 [Releases](https://github.com/LangQi99/Mekanical-Create/releases) 下载文件名带有 `mc1.20.1` 的 `mekanicalcreate-*.jar`。
4. 将 JAR 放入游戏实例的 `mods` 文件夹后启动游戏。

## 开发构建

```bash
./gradlew build
./gradlew runClient
```

项目通过公开依赖引用 Create 与 Mekanism，不会将两者的源码或资源直接打包进本模组。运行时使用的 Mekanism 模型、贴图、GUI 与音效仍遵循其各自项目的许可协议。

## 许可证

Mekanical Create 使用 [MIT License](LICENSE) 开源。Create 与 Mekanism 的内容仍分别受其自身许可证约束。
