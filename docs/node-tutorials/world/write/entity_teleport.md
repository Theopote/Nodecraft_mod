# Teleport Entity

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.entity_teleport` |
| 分类 | `world.write` |
| 显示名称 | Teleport Entity |
| 效果 | `WORLD_WRITE` |

## 作用

传送实体到 POINT（同维度）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Entity | MINECRAFT_ENTITY |
| 入 | Entities | MINECRAFT_ENTITY_LIST |
| 入 | Destination | POINT |
| 入 | Trigger | BOOLEAN |
| 入 | Preserve Rotation | BOOLEAN |
| 入 | Yaw | DOUBLE |
| 入 | Pitch | DOUBLE |
| 入 | Reset Fall Distance | BOOLEAN |
| 入 | Max Count | INTEGER |
| 出 | Success Count | INTEGER |
| 出 | Failure Count | INTEGER |
| 出 | Total Count | INTEGER |
| 出 | All Success | BOOLEAN |
| 出 | Teleported Entities | MINECRAFT_ENTITY_LIST |
| 出 | Complete | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Teleport Entity].Entity
[Teleport Entity].Success Count ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
