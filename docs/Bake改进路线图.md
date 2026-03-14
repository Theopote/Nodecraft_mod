# NodeCraft Bake（烘焙）系统改进路线图

本文档基于 Grasshopper/Dynamo 风格工作流的建筑设计建议，描述 NodeCraft 在**体素化预览**与**永久放置**方面的改进规划。

---

## 一、核心策略：两步走

1. **预览层**：在虚拟缓存中显示，不修改世界，支持参数实时调整。
2. **Bake 层**：用户确认后，将预览结果永久应用到世界（支持异步、模式、撤销）。

---

## 二、改进维度与实现状态

### 1. 虚拟几何体缓存 (Ghost Buffer)

| 建议 | 说明 | 状态 |
|------|------|------|
| 分层渲染 | 不直接在世界放方块，使用 VertexConsumer/LevelRenderer 渲染虚影 | 规划中 |
| BlockPos→BlockState 缓存 | GeometryViewerNode 维护内部 Map 缓存，参数变更时仅更新缓存并触发重绘 | 进行中 |
| SDF 节点 | 引入 SDF (Signed Distance Fields)，方便复杂形状并集/交集计算 | 规划中 |
| Voxelizer 节点 | 将 SDF 统一转化为方块坐标列表，配合 spatial.voxel 使用 | 规划中 |

**已实现**：GeometryViewerNode 增加 `geometryCache` (Map<BlockPos, BlockState>) 和 `cacheDirty` 脏标记，只有输入变化时重新计算并更新预览。

---

### 2. Bake 逻辑强化

| 建议 | 说明 | 状态 |
|------|------|------|
| 异步放置 | 大批量放置时分 tick 处理（如每 tick 1000 方块），避免主线程阻塞 | 已实现 |
| 覆盖模式 | 直接替换目标位置原有方块 | 已实现 |
| 增量模式 | 仅在空气位置放置，保留已有方块 | 已实现 |
| 放置模式 UI | ApplyChangesNode / GeometryViewerNode 中可选择模式 | 已实现 |

**已实现**：`BakePlacementService` 每 tick 批量处理队列；`PlacementMode` 枚举支持 OVERWRITE / INCREMENTAL。

---

### 3. 方案管理与回滚

| 建议 | 说明 | 状态 |
|------|------|------|
| 撤销堆栈 | 永久放置时记录被覆盖的原有方块，支持 UndoLastBake | 已实现 |
| UndoLastBakeNode | 或 ImGUI 面板中的历史记录，一键恢复上次 Bake | 已实现骨架 |
| Schematic 导出 | 将 Bake 结果导出为 .schematic 或 .litematic | 规划中 |

**已实现**：`BakeHistory` 记录每次 Bake 的 (BlockPos, BlockState) 列表；`BakePlacementService.undoLast()` 可恢复。

---

### 4. 材质与属性解耦

| 建议 | 说明 | 状态 |
|------|------|------|
| 材质代理 | 形状生成输出 POINT_LIST/REGION，材质节点单独分配 BlockType | 规划中 |
| 草稿方块 | 先用彩色羊毛等调整形状，再用 MaterialMapper 一键更换材质 | 规划中 |

---

### 5. 性能：差异化更新 (Delta Updates)

| 建议 | 说明 | 状态 |
|------|------|------|
| 脏标记 | 参数未改变时不重新体素化，仅当节点参数确实变化时更新 | 已实现 |
| 增量预览 | 大形状仅更新变化部分（高级优化） | 规划中 |

**已实现**：GeometryViewerNode 使用 `cacheDirty`，输入不变时跳过预览更新。

---

## 三、关键 API

### PlacementMode
```java
public enum PlacementMode {
    OVERWRITE,   // 覆盖模式：直接替换
    INCREMENTAL  // 增量模式：仅在空气位置放置
}
```

### BakePlacementService
- `enqueue(world, blocks, mode, recordUndo)`：将放置任务加入队列
- `processTick()`：每 tick 处理一批（由 ServerTickEvents 调用）
- `undoLast()`：撤销最近一次 Bake（若启用 recordUndo）

### GeometryViewerNode
- `placementMode`：PlacementMode，支持 UI 选择
- `useAsyncBake`：是否使用异步队列（大批量时建议开启）
- `geometryCache`：BlockPos→BlockState 缓存
- `cacheDirty`：输入变化时置 true，触发重算

---

## 四、后续计划

1. **SDF + Voxelizer**：引入 spatial.sdf 分类，实现 SDF 并/交/差运算及 Voxelizer 节点。
2. **Schematic 导出**：Bake 完成后可选导出为 .schematic/.litematic。
3. **MaterialMapper 节点**：按高度、斜率等属性分配不同 BlockType。
4. **分层渲染**：将幽灵方块从当前实现迁移到 VertexConsumer 渲染，提升大体积预览性能。
