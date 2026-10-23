# Remove Entities

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.remove_entities` |
| 分类 | `world.write` |
| 显示名称 | Remove Entities |
| 效果 | `WORLD_WRITE` |

## 作用

移除实体；查找属 world.query。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Entity | MINECRAFT_ENTITY |
| 入 | Entities | MINECRAFT_ENTITY_LIST |
| 入 | Trigger | BOOLEAN |
| 入 | Drop Items | BOOLEAN |
| 入 | Max Count | INTEGER |
| 出 | Removed Count | INTEGER |
| 出 | Failure Count | INTEGER |
| 出 | Success | BOOLEAN |
| 出 | Failed Entities | MINECRAFT_ENTITY_LIST |
| 出 | Complete | BOOLEAN |
| 出 | Hit Limit | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Remove Entities].Entity
[Remove Entities].Removed Count ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
