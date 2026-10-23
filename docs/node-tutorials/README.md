# NodeCraft 节点使用教程

面向使用者的中文教程：按节点库分类组织，说明每个节点做什么、端点怎么接、常见用法与注意点。

节点显示名称与端点名保持英文（与编辑器一致）；说明文字用中文。

**进度：539 / 539（100%）。** 勾选明细见 [`PROGRESS.md`](./PROGRESS.md)。分类真源：[`NODE_LIBRARY.zh-CN.md`](../NODE_LIBRARY.zh-CN.md)。

## 推荐阅读路径

主建模闭环（从输入到落世界）：

```mermaid
flowchart LR
  In[input / reference] --> Geo[geometry]
  Geo --> Xform[transform / pattern]
  Xform --> Mat[material]
  Mat --> Out[output.preview / execute]
  Out --> World[world.read / write]
```

1. [`flow`](./flow/control/) — 执行流入口  
2. [`input`](./input/context/) · [`reference`](./reference/planes/) — 值与坐标系  
3. [`geometry`](./geometry/primitives/) — 原语 / 轮廓 / 曲线 / 实体 / 建筑  
4. [`transform`](./transform/basic_transforms/) · [`pattern`](./pattern/grid/) — 变换与阵列  
5. [`material`](./material/basic_assignment/) — 赋材质  
6. [`output`](./output/execute/) · [`world`](./world/write/) — 预览、烘焙、读写世界  
7. [`math`](./math/scalar_math/) · [`utilities`](./utilities/assist/) · [`variable`](./variable/) — 运算与辅助  

## 分类索引

### flow / input / reference

| 分类 | 说明 |
|---|---|
| [flow.control](./flow/control/) · [flow.loop](./flow/loop/) | 分支、序列、循环 |
| [input.context](./input/context/) · [values](./input/values/) · [type_selectors](./input/type_selectors/) · [numeric](./input/numeric/) | 上下文、控件、选择器、数值 |
| [reference.planes](./reference/planes/) · [frames](./reference/frames/) · [points](./reference/points/) · [vectors](./reference/vectors/) | 平面 / 坐标系 / 点 / 向量 |

### geometry

| 分类 | 说明 |
|---|---|
| [primitives](./geometry/primitives/) · [profiles](./geometry/profiles/) · [curves](./geometry/curves/) · [solids](./geometry/solids/) · [sdf](./geometry/sdf/) | 主建模链 |
| [architectural_primitives](./geometry/architectural_primitives/) | 墙窗柱梁屋顶楼梯 |
| [boolean](./geometry/boolean/) · [combine](./geometry/combine/) · [analysis](./geometry/analysis/) · [voxel](./geometry/voxel/) | 布尔 / 组合 / 包围盒 / 体素化 |

### transform / pattern / material

| 分类 | 说明 |
|---|---|
| [basic_transforms](./transform/basic_transforms/) · [deformations](./transform/deformations/) · [orientation](./transform/orientation/) · [placement](./transform/placement/) | 变换与放置 |
| [grid](./pattern/grid/) · [linear](./pattern/linear/) · [radial](./pattern/radial/) · [lsystem](./pattern/lsystem/) · [scatter](./pattern/surface_volume_distribution/) · [voronoi](./pattern/voronoi_3d/) | 阵列与散射 |
| [basic_assignment](./material/basic_assignment/) · [block_state](./material/block_state/) · [directional](./material/directional_mapping/) · [gradient](./material/gradient_mapping/) · [pattern](./material/pattern_mapping/) · [aging](./material/surface_aging/) | 材质与老化 |

### output / world

| 分类 | 说明 |
|---|---|
| [debug](./output/debug/) · [execute](./output/execute/) · [export](./output/export/) · [preview](./output/preview/) | 调试、烘焙、导出、幽灵预览 |
| [query](./world/query/) · [read](./world/read/) · [selection](./world/selection/) · [terrain](./world/terrain/) · [write](./world/write/) | 查询、读取、选区、地形场、写入 |

### math / utilities / variable

| 分类 | 说明 |
|---|---|
| [compare](./math/compare/) · [logic](./math/logic/) · [scalar_math](./math/scalar_math/) · [trigonometry](./math/trigonometry/) · [sequence](./math/sequence/) · [random](./math/random/) | 标量 / 逻辑 / 随机 |
| [list](./math/list/) · [data_tree](./math/data_tree/) · [fields](./math/fields/) · [vector](./math/vector/) | 列表、数据树、场 |
| [assist](./utilities/assist/) · [fileio](./utilities/fileio/) · [morphology](./utilities/morphology/) · [organization](./utilities/organization/) | 辅助与图组织 |
| [variable](./variable/) | 用户变量 |

## 写作约定

1. 文件名用节点 ID 最后一段，例如 `flow.control.branch` → `branch.md`。
2. 结构对照 [`_template.md`](./_template.md)，可按节点复杂度删减小节。
3. 端点表写清：显示名、类型、方向；复杂节点可补端口 ID。
4. 「使用步骤」面向第一次用这个节点的人；「典型接法」给可照抄的文字连线。
5. 实现细节以源码 `@NodeInfo` 与端口定义为准。
6. **图文**：复杂空间概念可加 Mermaid，或放在分类下 `assets/`（如 `player_raycast.md`）。带「示意图」的节点约 65：建筑、布尔/SDF、实体曲面、变形、阵列散射、数据树/场、地形侵蚀、流程分支、预览等（文内搜 `## 示意图`）。

## 目录结构

```text
docs/node-tutorials/
├── README.md          ← 本说明
├── PROGRESS.md        ← 进度追踪
├── _template.md       ← 单节点教程模板
├── flow/ · geometry/ · input/ · material/
├── math/ · output/ · pattern/ · reference/
├── transform/ · utilities/ · variable/ · world/
```
