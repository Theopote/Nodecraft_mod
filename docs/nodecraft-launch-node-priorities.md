# NodeCraft 上线节点清单建议

本文基于以下两部分交叉整理：

- `NodeCraft_节点框架清单.docx` 中的节点规划状态
- 当前仓库 `src/main/java/com/nodecraft/nodesystem/nodes` 的实际实现情况

目标不是继续扩充一份更大的节点总表，而是回答一个更实际的问题：

`NodeCraft` 要“正常上线工作”，哪些节点必须先补实，哪些节点首发后尽快补，哪些节点可以放到后续增强。

## 结论摘要

当前最需要优先处理的不是再追加一批高级几何节点，而是把以下三类能力补成产品级闭环：

- 世界读取与世界写入节点中的 `stub/mock/demo` 实现
- 可回退的执行链路，尤其是正式的撤销节点
- 建筑生产力节点，包括材质分配、路径阵列、立面分格、地形读取

同时，清单文档里的状态并不完全准确。有些节点在文档中标为“规划中”，但代码里已经有基础实现；真正影响上线的是“代码存在但不可靠”的节点。

## 状态判定标准

为了后续排期更清楚，建议把节点状态按下面四类管理，而不是只看“已实现/规划中/待扩展”：

- `已完成`：真实 API 可用，行为稳定，可进入生产
- `半成品`：代码存在，但包含示例逻辑、模拟结果、占位行为，不能作为稳定能力对外承诺
- `未实现`：文档有，代码无
- `待重构`：代码有，但节点 ID、分类、输出行为或文档状态与实际不一致

## P0 必做

这些节点或能力不补，`NodeCraft` 不适合正式上线。

### 1. 世界交互节点补实

这批节点的共同问题是：代码里已经有类，但实现中仍出现“模拟”“示例代码”“占位返回”等痕迹。

| 节点 ID | 当前判断 | 主要问题 | 代码位置 |
|---|---|---|---|
| `world.write.spawn_entity` | 半成品 | 仍是模拟生成实体，返回假的实体对象和 UUID | [SpawnEntityNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/SpawnEntityNode.java) |
| `world.write.execute_command` | 半成品 | 命令执行仍是模拟结果，不是实际命令源与返回链路 | [ExecuteCommandNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/ExecuteCommandNode.java) |
| `world.write.simulate_right_click` | 半成品 | 交互流程仍有模拟逻辑 | [SimulateRightClickNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/SimulateRightClickNode.java) |
| `world.write.apply_redstone_power` | 半成品 | 仍是模拟施加红石行为 | [ApplyRedstonePowerNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/ApplyRedstonePowerNode.java) |
| `world.read.read_sign_text` | 半成品 | 告示牌文本读取含模拟逻辑 | [ReadSignTextNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/ReadSignTextNode.java) |
| `world.write.write_sign_text` | 半成品 | 告示牌文本写入含模拟逻辑 | [WriteSignTextNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/WriteSignTextNode.java) |

建议：

- 这组节点统一按“真实 Minecraft API 接入”一轮做完
- 不建议单点修补，否则上线后用户会遇到“有的世界节点能用，有的只是演示”的割裂体验

### 2. 世界读取节点补真数据

这批节点如果继续输出模拟值，会污染整张节点图的结果，问题比“节点缺失”更严重。

| 节点 ID | 当前判断 | 主要问题 | 代码位置 |
|---|---|---|---|
| `world.read.get_biome` | 半成品 | 存在示例代码与模拟值，如 `minecraft:plains` | [GetBiomeNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/GetBiomeNode.java) |
| `world.read.biome_at_player` | 半成品 | 仍有默认假值输出风险 | [BiomeAtPlayerNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/BiomeAtPlayerNode.java) |
| `world.query.get_light_level` | 半成品 | 仍是示例式读取 | [GetLightLevelNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/query/GetLightLevelNode.java) |
| `world.query.get_fluid_level` | 半成品 | 仍是示例式读取 | [GetFluidLevelNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/query/GetFluidLevelNode.java) |
| `world.query.get_entities_in_region` | 半成品 | 仍是模拟区域实体扫描 | [GetEntitiesInRegionNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/query/GetEntitiesInRegionNode.java) |

建议：

- 优先保证查询节点返回真实世界数据
- 所有读取类节点统一补错误处理和“上下文不可用”时的稳定输出策略

### 3. 正式撤销节点

这项不是加分项，是上线安全底线。

当前现状：

- 执行层已经有撤销历史基础设施：[BakeHistory.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/bake/BakeHistory.java)
- 放置服务里已经在管理历史记录：[BakePlacementService.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/bake/BakePlacementService.java)
- 执行入口节点已经支持记录撤销：[ApplyChangesNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/output/execute/ApplyChangesNode.java)
- 但用户侧还缺一个正式、明确、可视化的撤销节点

建议新增：

- `output.execute.undo_last_bake`
- 或 `world.write.undo_last`

要求：

- 明确撤销作用域
- 明确是否只撤销 `ApplyChanges` / `Bake`
- 输出撤销结果、撤销块数、失败原因

### 4. 导出能力补强

当前 [ExportSchematicNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/output/export/ExportSchematicNode.java) 已有基础导出能力，但还不足以作为完整上线能力宣传。

主要问题：

- 当前更像基础 `nbt` 导出器
- 对外部生态兼容性仍不足
- 文件格式、元数据、用户预期名称与兼容格式之间还不够清晰

建议：

- 首发前先把 `export_schematic` 做稳
- 明确导出格式说明，不要过度承诺

## P1 首发强烈建议

这批节点不一定阻塞首发，但会直接决定用户能不能高效做建筑类工作流。

### 1. 地形与场地读取

| 节点 ID | 建议 | 原因 |
|---|---|---|
| `world.read.get_heightmap` | 新增 | 地形适配、自动落地、台地建筑都需要 |
| `world.read.get_surface_blocks` | 新增 | 快速识别地表材质和顶面很常用 |

### 2. 建筑生产力节点

| 节点 ID | 建议 | 原因 |
|---|---|---|
| `material.basic_assignment.block_palette` | 新增 | 当前材质分配过于单一，真实建筑工作流会很快卡住 |
| `pattern.linear.along_path` | 新增 | 栏杆、柱列、边缘构件、檐线都高频依赖 |
| `pattern.grid.facade_grid` | 新增 | 立面幕墙、窗洞、柱网是建筑场景高频需求 |
| `material.block_state.stair_shape` | 新增或增强 | 既然已有自动朝向，就应继续补楼梯转角逻辑 |

### 3. 类型选择器补齐

| 节点 ID | 建议 | 原因 |
|---|---|---|
| `input.type_selectors.item_type_selector` | 新增 | 与世界交互、物品相关能力匹配 |
| `input.type_selectors.entity_type_selector` | 新增 | 与 `spawn_entity` 能力配套 |

### 4. 布尔建模补完整

当前布尔能力的对外语义和内部实现还有差距。

现状：

- [GeometryUnionNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/geometry/boolops/GeometryUnionNode.java) 更偏组合容器
- [DifferenceNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/geometry/boolops/DifferenceNode.java) 更偏差集包装

建议：

- 新增 `geometry.solids.boolean_intersection`
- 统一 `union/difference/intersection` 的输出语义
- 明确它们到底是“解析几何布尔”还是“体素求值布尔”

如果不统一，用户会在预览、Bake、材质映射三个阶段看到不一致结果。

## P2 后续增强

这些节点有价值，但不该阻塞首发。

| 节点 ID | 建议原因 |
|---|---|
| `geometry.solids.revolve` | 很实用，但优先级低于基础建筑闭环 |
| `geometry.solids.shell` | 高级建模功能，适合第二阶段 |
| `geometry.solids.thicken_surface` | 同上 |
| `geometry.curves.arc` | 有用，但没有路径分布更急 |
| `geometry.curves.bezier` | 曲线质量增强 |
| `output.export.export_litematic` | 生态兼容增强 |
| `output.export.export_worldedit` | 生态兼容增强 |
| `utilities.fileio.read_image` | 高度图/贴图输入很有价值，但不是首发底线 |
| `material.gradient_mapping.noise_material` | 效果增强 |
| `pattern.surface_volume_distribution.poisson_scatter` | 高质量散布增强 |

## 文档状态不准的节点

以下节点在清单里并不总是被标为“已实现”，但代码里其实已有基础实现，不应继续算作真正缺失项。

| 节点 ID | 当前代码情况 | 代码位置 |
|---|---|---|
| `input.context.dimension_info` | 已有基础实现 | [DimensionInfoNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/input/context/DimensionInfoNode.java) |
| `input.context.current_time` | 已有基础实现 | [CurrentTimeNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/input/context/CurrentTimeNode.java) |
| `utilities.assist.reroute` | 已有基础实现 | [RerouteNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/utilities/assist/RerouteNode.java) |
| `math.list_sequence.get_item` | 已有基础实现 | [GetItemNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/math/list_sequence/GetItemNode.java) |
| `math.list_sequence.set_item` | 已有基础实现 | `src/main/java/com/nodecraft/nodesystem/nodes/math/list_sequence/SetItemNode.java` |
| `math.list_sequence.list_length` | 已有基础实现 | `src/main/java/com/nodecraft/nodesystem/nodes/math/list_sequence/ListLengthNode.java` |
| `math.list_sequence.filter_list` | 已有基础实现 | `src/main/java/com/nodecraft/nodesystem/nodes/math/list_sequence/FilterListNode.java` |

这意味着后续做产品清单时，要避免把“有基础实现”继续当成“未做节点”重复排期。

## 推荐排期顺序

## 当前实施进度

### 已完成的第一批 P0

本轮已经落地并通过 `compileJava` 验证的节点如下：

- `output.execute.undo_last_bake`
- `world.query.get_light_level`
- `world.query.get_fluid_level`
- `world.read.get_biome`
- `world.read.biome_at_player`
- `world.query.get_entities_in_region`

对应代码：

- [UndoLastBakeNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/output/execute/UndoLastBakeNode.java)
- [GetLightLevelNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/query/GetLightLevelNode.java)
- [GetFluidLevelNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/query/GetFluidLevelNode.java)
- [GetBiomeNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/GetBiomeNode.java)
- [BiomeAtPlayerNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/BiomeAtPlayerNode.java)
- [GetEntitiesInRegionNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/query/GetEntitiesInRegionNode.java)

### 已完成的第二批 P0

本轮已经继续补实并通过 `compileJava` 验证的节点如下：

- `world.read.read_sign_text`
- `world.write.write_sign_text`
- `world.write.spawn_entity`

对应代码：

- [ReadSignTextNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/ReadSignTextNode.java)
- [WriteSignTextNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/WriteSignTextNode.java)
- [SpawnEntityNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/SpawnEntityNode.java)

### 已完成的第三批 P0

本轮已经继续补实并通过 `compileJava` 验证的节点如下：

- `world.write.execute_command`
- `world.write.simulate_right_click`
- `world.write.apply_redstone_power`

对应代码：

- [ExecuteCommandNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/ExecuteCommandNode.java)
- [SimulateRightClickNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/SimulateRightClickNode.java)
- [ApplyRedstonePowerNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/ApplyRedstonePowerNode.java)
- [RedstonePulseService.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/write/RedstonePulseService.java)

### 下一批建议直接继续做的 P0

按收益和耦合度，建议下一轮按下面顺序继续：

1. `output.export.export_schematic` 增强
2. `world.read.get_heightmap`
3. `world.read.get_surface_blocks`
4. `material.basic_assignment.block_palette`
5. `pattern.linear.along_path`

### 第一阶段：上线底线

- 补实所有 `world.read/query/write` 中的示例和模拟实现
- 新增正式撤销节点
- 稳定 `apply_changes` 到撤销链路
- 明确 `export_schematic` 的格式边界和能力说明

### 第二阶段：建筑工作流

- 新增 `get_heightmap`
- 新增 `get_surface_blocks`
- 新增 `block_palette`
- 新增 `along_path`
- 新增 `facade_grid`
- 新增 `entity_type_selector`
- 新增 `item_type_selector`

### 第三阶段：高级建模与生态导出

- 完整布尔建模
- `revolve/shell/thicken`
- `litematic/worldedit` 导出
- 图像输入和高级散布

## 最终建议

如果目标是“能上线并让用户稳定完成建筑生成工作流”，建议研发口径改成：

- 先做“真实可用”
- 再做“建筑高频”
- 最后做“高级炫技”

对应到节点层，就是：

- 不要优先追加更多稀有几何节点
- 优先修复世界交互半成品节点
- 优先补撤销、场地读取、材质分配、路径阵列、立面分格

这条路线更接近一个能交付、能演示、也能让用户真的做事的 `NodeCraft v1`。

---

## 2026-04-19 实施进度追加

### 已完成的第一批 P1

本轮已经补实并通过 `compileJava` 验证的节点如下：

- `world.read.get_heightmap`
- `world.read.get_surface_blocks`
- `material.basic_assignment.block_palette`
- `pattern.linear.along_path`
- `pattern.grid.facade_grid`

对应代码：
- [GetHeightmapNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/GetHeightmapNode.java)
- [GetSurfaceBlocksNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/world/read/GetSurfaceBlocksNode.java)
- [BlockPaletteNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/material/basic_assignment/BlockPaletteNode.java)
- [AlongPathNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/pattern/linear/AlongPathNode.java)
- [FacadeGridNode.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/nodes/pattern/grid/FacadeGridNode.java)

### 当前建议的下一批 P1

按建筑工作流收益和实现耦合度，建议下一轮按下面顺序继续：

1. `material.block_state.stair_shape`
2. `input.type_selectors.entity_type_selector`
3. `input.type_selectors.item_type_selector`
4. `output.export.export_schematic` 增强
