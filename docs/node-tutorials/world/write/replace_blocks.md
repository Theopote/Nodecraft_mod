# Replace Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.replace_blocks` |
| 分类 | `world.write` |
| 显示名称 | Replace Blocks |
| 效果 | `WORLD_WRITE` |

## 作用

区域或坐标列表内替换匹配方块。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Coordinates | BLOCK_LIST |
| 入 | Target Block | BLOCK_INFO |
| 入 | Replacement Block | BLOCK_INFO |
| 入 | Trigger | BOOLEAN |
| 入 | Exact Match | BOOLEAN |
| 入 | Notify Update | BOOLEAN |
| 入 | Spawn Drops | BOOLEAN |
| 入 | Max Blocks | INTEGER |
| 出 | Replaced Blocks | INTEGER |
| 出 | Checked Blocks | INTEGER |
| 出 | Success Count | INTEGER |
| 出 | Failure Count | INTEGER |
| 出 | Total Count | INTEGER |
| 出 | Affected Coordinates | BLOCK_LIST |
| 出 | Hit Limit | BOOLEAN |
| 出 | Complete | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Replace Blocks].Region
[Replace Blocks].Replaced Blocks ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
