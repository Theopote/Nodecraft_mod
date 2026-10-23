# Fill Region

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.fill_region` |
| 分类 | `world.write` |
| 显示名称 | Fill Region |
| 效果 | `WORLD_WRITE` |

## 作用

区域填充满一种方块。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Block Info | BLOCK_INFO |
| 入 | Trigger | BOOLEAN |
| 入 | Hollow | BOOLEAN |
| 入 | Exclude Air | BOOLEAN |
| 入 | Notify Update | BOOLEAN |
| 入 | Spawn Drops | BOOLEAN |
| 入 | Max Blocks | INTEGER |
| 出 | Filled Blocks | INTEGER |
| 出 | Affected Blocks | INTEGER |
| 出 | Success Count | INTEGER |
| 出 | Failure Count | INTEGER |
| 出 | Total Count | INTEGER |
| 出 | Coordinates | BLOCK_LIST |
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
[上游].… ──► [Fill Region].Region
[Fill Region].Filled Blocks ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
