# Find Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.find_blocks` |
| 分类 | `world.read` |
| 显示名称 | Find Blocks |
| 效果 | `WORLD_READ` |

## 作用

区域内按类型或精确状态查找位置。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Block Info | BLOCK_INFO |
| 入 | Target Block Type | BLOCK_TYPE |
| 入 | Exact Match | BOOLEAN |
| 入 | Max Results | INTEGER |
| 入 | Max Blocks | INTEGER |
| 出 | Found Blocks | BLOCK_LIST |
| 出 | Count | INTEGER |
| 出 | First Position | BLOCK_POS |
| 出 | Found Any | BOOLEAN |
| 出 | Visited Count | INTEGER |
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
[上游].… ──► [Find Blocks].Region
[Find Blocks].Found Blocks ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
