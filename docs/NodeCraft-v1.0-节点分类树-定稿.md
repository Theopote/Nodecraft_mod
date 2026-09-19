# NodeCraft v1.0 节点分类树定稿

> **已降级为设计历史，不再作为当前实现规范。**  
> 当前节点 / 分类真源：仓库代码中的 `@NodeInfo`，以及生成的
> [`NODE_LIBRARY.md`](./NODE_LIBRARY.md) / [`NODE_LIBRARY.zh-CN.md`](./NODE_LIBRARY.zh-CN.md)。  
> 权威冻结：[`architecture/docs-authority.md`](./architecture/docs-authority.md)。

本文档用于定义 NodeCraft v1.0 重构后的主节点系统分类树，作为当时节点迁移、节点命名、注册体系、文档编写与 UI 分类展示的统一依据（**历史定稿**）。

本文档聚焦以下目标域：

- 建筑造型
- 几何建模
- 世界落地建造

本文档暂不纳入以下范围：

- 动画
- 植物
- NBT
- Inventory
- 实验性节点

## 1. 设计目标

NodeCraft v1.0 的节点分类体系应满足以下要求：

- 分类结构稳定，能够作为中长期 canonical taxonomy 使用。
- 分类语义优先于当前代码包结构，避免被历史实现绑死。
- 节点只允许有一个 canonical category，避免重复归类。
- 分类树优先服务主建模路径：参照 -> 几何 -> 变换 -> 阵列 -> 材质 -> 世界落地。
- 世界操作与几何建模职责分离，避免“既是建模节点又是执行节点”的混乱。
- 允许通过搜索、快捷入口、别名提高易用性，但不改变 canonical home。

## 2. 分类原则

### 2.1 Canonical Home

每个节点必须且只能属于一个正式分类，即其 canonical home。

- 分类树中的位置用于：
  - 节点注册
  - 节点文档
  - 节点搜索分组
  - 迁移映射
  - 后续 node id / category id 设计
- 快捷入口、收藏、搜索关键词、别名、推荐入口不视为正式分类。

### 2.2 语义优先

分类按节点在工作流中的语义职责划分，而不是按内部实现方式划分。

- “读取世界”归入 `World`
- “建立参照系”归入 `Reference`
- “生成几何体”归入 `Geometry`
- “将结果写入世界”归入 `Output.Execute` 或 `World.Write`

### 2.3 主路径优先

NodeCraft v1.0 的主路径定义为：

`Input / Reference -> Geometry / Transform / Pattern -> Material -> Output / World`

因此分类设计应优先支持以下使用习惯：

- 先建立点、向量、平面、局部坐标系
- 再生成几何与建筑体量
- 再进行阵列、分布、变换与材质映射
- 最后进行预览、提交与落地建造

## 3. 顶级分类树

```text
Input
├─ Numeric
├─ Context
└─ Type Selectors

Reference
├─ Points
├─ Vectors
├─ Planes
└─ Frames

Geometry
├─ Primitives
├─ Curves
├─ Profiles
├─ Solids
├─ Boolean
└─ Architectural Primitives

Transform
├─ Basic Transforms
├─ Orientation
└─ Deformations

Pattern
├─ Linear
├─ Grid
├─ Radial
├─ Along Curve
└─ Surface / Volume Distribution

Material
├─ Basic Assignment
├─ Gradient Mapping
├─ Directional Mapping
├─ Pattern Mapping
├─ Block State
└─ Surface Aging

World
├─ Read
├─ Query
├─ Write
└─ Selection

Output
├─ Preview
├─ Execute
├─ Export
└─ Debug

Math & Logic
├─ Scalar Math
├─ Compare
├─ Trigonometry
├─ Logic
├─ Random
└─ List / Sequence
```

## 4. 各顶级分类职责

### 4.1 Input

用于提供最基础、最直接的人为输入与上下文起点。

职责：

- 提供数值输入
- 提供布尔或角度类输入
- 提供玩家上下文
- 提供材料、方块类型等基础选择器

不负责：

- 世界查询
- 几何构造
- 列表处理
- 正式世界选择归档

### 4.2 Reference

用于建立建模过程中的空间参照。

职责：

- 定义点、向量、平面
- 建立局部坐标系或方向框架
- 为几何和变换提供稳定的定位基础

这是 v1.0 主建模系统的基础层。

### 4.3 Geometry

用于生成、组合和编辑几何体。

职责：

- 提供基础体
- 提供曲线与截面
- 提供从轮廓到实体的生成操作
- 提供布尔运算
- 提供带建筑语义的原型体

这是建筑造型与几何建模的核心层。

### 4.4 Transform

用于改变对象的位置、朝向和形变。

职责：

- 平移、旋转、缩放、镜像
- 面向平面、向量、轴的定向
- 扭曲、弯曲、锥化等高级变形

### 4.5 Pattern

用于将对象按规则重复、分布或布置。

职责：

- 线性阵列
- 网格阵列
- 极向阵列
- 沿曲线布置
- 表面或体积分布

### 4.6 Material

用于定义几何结果如何映射为方块与方块状态。

职责：

- 基础赋材
- 基于高度、距离、方向的映射
- 基于图案或噪声的映射
- 方块状态规则
- 表面风化与旧化

这一层负责“用什么方块建”，而不是“如何生成几何”。

### 4.7 World

用于读取、查询、选择和直接修改 Minecraft 世界。

职责：

- 读取世界信息
- 查询环境状态
- 管理世界选区
- 提供低级直接写入能力

这一层不是主建模路径中心，但必须作为落地建造与环境感知接口存在。

### 4.8 Output

用于预览、执行、导出与调试。

职责：

- 可视化预览
- 将建模结果提交到世界
- 导出结构或放置数据
- 调试节点值与执行过程

### 4.9 Math & Logic

用于提供全局通用的数学、条件、随机与序列控制能力。

职责：

- 标量数学
- 比较判断
- 三角函数
- 布尔逻辑
- 随机与噪声
- 列表与序列处理

这是所有顶级域的通用支撑层。

## 5. 正式分类树与节点清单

## 5.1 Input

### 5.1.1 Numeric

用于提供最基础的参数输入。

节点：

- Integer
- Float
- Angle
- Boolean Toggle

### 5.1.2 Context

用于读取玩家当前状态或世界中的基础参照。

节点：

- Player Position
- Player Look Direction

### 5.1.3 Type Selectors

用于选择材料或规则输入的基础类型。

节点：

- Block Type Selector
- Block State Preset Selector

## 5.2 Reference

### 5.2.1 Points

用于建立位置参照。

节点：

- Point
- World Origin
- Mid Point
- Point From Block
- Point From Coordinates

### 5.2.2 Vectors

用于定义方向、偏移、法线、朝向。

节点：

- Vector
- Unit X
- Unit Y
- Unit Z
- Vector From Points
- Normalize Vector
- Cross Product
- Dot Product

### 5.2.3 Planes

用于建立建模基准平面。

节点：

- World Plane
- Construct Plane
- Plane From Points
- Block Face Plane
- Player Face Plane
- Offset Plane
- Rotate Plane
- Deconstruct Plane

### 5.2.4 Frames

用于定义更复杂的局部坐标系与方向框架。

节点：

- Construct Frame
- Frame From Plane
- Deconstruct Frame
- Align Frame To Vector

## 5.3 Geometry

### 5.3.1 Primitives

最常用、最高频的基础形体。

节点：

- Box
- Sphere
- Cylinder
- Cone
- Torus
- Ellipsoid
- Disk / Circle
- Capsule
- Prism

### 5.3.2 Curves

用于路径、轮廓、扫掠、阵列基线。

节点：

- Line
- Polyline
- Arc
- Bezier Curve
- Helix / Spiral
- Interpolated Curve
- Curve From Points

### 5.3.3 Profiles

用于生成实体的二维轮廓。

节点：

- Rectangle Profile
- Circle Profile
- Polygon Profile
- Arch Profile
- Custom Profile

### 5.3.4 Solids

从曲线或截面生成体量。

节点：

- Extrude
- Revolve
- Loft
- Sweep
- Shell / Offset Solid
- Cap Open Solid

### 5.3.5 Boolean

建筑建模核心组。

节点：

- Union
- Difference
- Intersection
- Split
- Trim By Plane
- Clip Geometry

### 5.3.6 Architectural Primitives

带建筑语义、但仍属于几何层。

节点：

- Arch
- Dome
- Wedge
- Staircase
- Vault
- Ramp

## 5.4 Transform

### 5.4.1 Basic Transforms

用于位置、旋转、缩放控制。

节点：

- Move
- Rotate
- Scale
- Mirror
- Align To
- Recenter

### 5.4.2 Orientation

用于改变对象相对于平面、向量、框架的朝向。

节点：

- Orient To Plane
- Align Axis
- Project To Plane
- Rotate Around Pivot

### 5.4.3 Deformations

用于高级造型。

节点：

- Bend
- Twist
- Taper
- Shear
- Noise Displace
- Warp To Sphere

## 5.5 Pattern

### 5.5.1 Linear

节点：

- Linear Array
- Repeat Along Vector
- Step Repeat

### 5.5.2 Grid

节点：

- Grid Array
- Staggered Grid
- Rectangular Tiling
- Brick Pattern Grid

### 5.5.3 Radial

节点：

- Polar Array
- Radial Repeat
- Ring Distribution

### 5.5.4 Along Curve

节点：

- Curve Array
- Evenly Divide Curve
- Place Along Curve
- Frame Along Curve

### 5.5.5 Surface / Volume Distribution

节点：

- Surface Scatter
- Volume Scatter
- Populate Region
- Populate By Mask

## 5.6 Material

### 5.6.1 Basic Assignment

节点：

- Assign Block Type
- Assign Multiple Block Types
- Replace Material

### 5.6.2 Gradient Mapping

节点：

- Height Gradient Map
- Distance Gradient Map
- Radial Gradient Map

### 5.6.3 Directional Mapping

节点：

- Face Normal Map
- Top / Side / Bottom Map
- Orientation-Based Material Map

### 5.6.4 Pattern Mapping

节点：

- Checker Pattern Map
- Stripe Pattern Map
- Noise Material
- Random Mix Material
- Custom Pattern Map

### 5.6.5 Block State

节点：

- Block State Assign
- Auto Orient Blocks
- Stair / Slab Orientation Rule
- Fence / Wall State Rule

### 5.6.6 Surface Aging

节点：

- Weathering
- Moss / Crack Overlay
- Edge Wear Map

## 5.7 World

### 5.7.1 Read

节点：

- Get Block
- Get Blocks In Region
- Find Blocks
- Get Biome
- Get Height Map
- Get Surface Blocks

### 5.7.2 Query

节点：

- Get Light Level
- Get Fluid Level
- Check Replaceable
- Check Loaded Chunks
- Is Solid Block

### 5.7.3 Write

保留低级世界直接写入能力，但不是主建模路径中心。

节点：

- Set Block
- Set Blocks
- Fill Region
- Replace Blocks
- Clone Region
- Clear Region

### 5.7.4 Selection

用于 Minecraft 世界对象或范围选取。

节点：

- Selected Block
- Selected Region

## 5.8 Output

### 5.8.1 Preview

节点：

- Geometry Viewer
- Preview Blocks
- Preview Points
- Preview Curves
- Preview Regions
- Preview Normals
- Preview Labels

### 5.8.2 Execute

用于将建模结果正式提交到世界。

节点：

- Apply Changes
- Bake Geometry To Blocks
- Commit Placements
- Undo Last Build
- Clear Preview

### 5.8.3 Export

节点：

- Export Schematic
- Export Placement List

### 5.8.4 Debug

节点：

- Value Monitor
- Print To Chat
- Execution Timer
- Data Inspector

## 5.9 Math & Logic

### 5.9.1 Scalar Math

节点：

- Math
- Clamp
- Remap
- Round
- Min / Max
- Abs
- Power

### 5.9.2 Compare

节点：

- Compare
- In Range
- Equals
- Greater / Less

### 5.9.3 Trigonometry

节点：

- Sin
- Cos
- Tan
- Atan2
- Degrees ↔ Radians

### 5.9.4 Logic

节点：

- And
- Or
- Not
- If
- Switch

### 5.9.5 Random

节点：

- Random Number
- Random Integer
- Seeded Random
- Noise Sample

### 5.9.6 List / Sequence

节点：

- Create List
- List Length
- Get Item
- Slice List
- Repeat List
- Range
- Zip Lists

## 6. 边界与归属规则

### 6.1 World.Selection 与 Input 的边界

`Selected Block` 和 `Selected Region` 的 canonical home 统一归入 `World.Selection`。

规则：

- 不在 `Input` 中重复设立正式分类位置
- 可以在 UI 中作为快捷入口暴露
- 可以在搜索中同时命中 “Input” 语义词
- 但文档、注册、迁移时均视为 `World.Selection`

### 6.2 List / Sequence 与 Input 的边界

`Create List` 不属于输入节点，统一归入 `Math & Logic > List / Sequence`。

原因：

- 它的职责是数据构造，而不是参数输入
- 它应与列表切片、范围、重复、拉链等序列操作同层

### 6.3 World.Write 与 Output.Execute 的边界

二者必须明确区分。

`World.Write`：

- 面向低级世界写入
- 强调直接操作方块或区域
- 可独立于几何建模存在

`Output.Execute`：

- 面向主建模路径的结果提交
- 强调将 Geometry / Material / Placement 结果统一落地
- 应承担预览转正式提交、构建提交、撤销等职责

### 6.4 Material 与 Geometry 的边界

`Material` 负责“如何映射方块与状态”，不负责生成几何体。

因此：

- 体量生成在 `Geometry`
- 分布规则在 `Pattern`
- 方块类型、图案、风化规则在 `Material`

### 6.5 Reference 与 Transform 的边界

`Reference` 负责建立参照本体，`Transform` 负责修改既有对象。

因此：

- Construct Plane 属于 `Reference`
- Offset Plane / Rotate Plane 仍属于 `Reference`
  - 因为结果仍是“新的参照平面”
- Move / Rotate / Scale / Mirror 属于 `Transform`
  - 因为结果是对几何或对象的变换

## 7. 暂不纳入范围

以下内容不属于 NodeCraft v1.0 主节点分类树的当前范围：

- Animation
- Flora
- NBT
- Inventory
- Entity 行为控制
- 命令执行类节点
- Script / Eval / Attribute 等高级工作流节点
- 纯实验性质节点

这些内容可以在后续版本作为扩展域重新设计，但不进入本次主树定稿。

## 8. 对后续实现的约束

本分类树定稿后，后续实现应遵守以下约束：

- 新节点必须优先映射到本树中的 canonical category。
- 不得为了兼容旧实现而在正式分类树中制造重复归属。
- 历史节点迁移可以通过 alias、搜索别名、UI 快捷入口解决，不通过重复分类解决。
- 旧系统中的 `spatial`、`visualization`、`inputs`、`world` 等历史分类，不应继续作为 v1.0 主分类直接沿用。
- 后续 category id、node id、文档目录与 UI 展示顺序都应以本树为准。

## 9. 下一步建议

在本定稿基础上，下一阶段建议按以下顺序推进：

1. 确定英文 category id 规范
2. 制定旧分类到新分类的迁移映射表
3. 梳理现有节点的 canonical home
4. 制定 node id 重命名与 alias 兼容规则
5. 最后再进入注册器、扫描器、UI 节点库与文档体系改造
