# Get Blocks In Region

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_blocks_in_region` |
| 分类 | `world.read` |
| 显示名称 | Get Blocks In Region |
| 效果 | `WORLD_READ` |

## 作用

区域内块状态与坐标（有扫描上限）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Exclude Air | BOOLEAN |
| 入 | Max Blocks | INTEGER |
| 出 | Blocks List | BLOCK_INFO_LIST |
| 出 | Coordinates | BLOCK_LIST |
| 出 | Scanned Count | INTEGER |
| 出 | Output Count | INTEGER |
| 出 | Air Count | INTEGER |
| 出 | Solid Count | INTEGER |
| 出 | Total Possible | INTEGER |
| 出 | Hit Limit | BOOLEAN |
| 出 | Complete | BOOLEAN |
| 出 | Stopped Reason | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Blocks In Region].Region
[Get Blocks In Region].Blocks List ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
