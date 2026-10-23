# Simulate Right Click

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.simulate_right_click` |
| 分类 | `world.write` |
| 显示名称 | Simulate Right Click |
| 效果 | `WORLD_WRITE` |

## 作用

服务端模拟对方块右键。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 入 | Trigger | BOOLEAN |
| 入 | Player | PLAYER |
| 入 | Item in Hand | ITEM_STACK |
| 入 | Play Sound | BOOLEAN |
| 出 | Success | BOOLEAN |
| 出 | Block Type | STRING |
| 出 | Interaction Result | ANY |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Simulate Right Click].Coordinate
[Simulate Right Click].Success ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
