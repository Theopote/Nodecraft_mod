# Set Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.set_blocks` |
| 分类 | `world.write` |
| 显示名称 | Set Blocks |
| 效果 | `WORLD_WRITE` |

## 作用

显式坐标批量放置，可选共享 NBT。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinates | BLOCK_LIST |
| 入 | Block Info | BLOCK_INFO |
| 入 | Block Info List | BLOCK_INFO_LIST |
| 入 | Trigger | BOOLEAN |
| 入 | Notify Update | BOOLEAN |
| 入 | Spawn Drops | BOOLEAN |
| 入 | Max Blocks | INTEGER |
| 入 | NBT | NBT_COMPOUND |
| 入 | NBT String | STRING |
| 入 | Merge NBT | BOOLEAN |
| 出 | Success Count | INTEGER |
| 出 | Failure Count | INTEGER |
| 出 | NBT Success Count | INTEGER |
| 出 | Total Count | INTEGER |
| 出 | All Success | BOOLEAN |
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
[上游].… ──► [Set Blocks].Coordinates
[Set Blocks].Success Count ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
