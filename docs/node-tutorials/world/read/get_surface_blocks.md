# Get Surface Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_surface_blocks` |
| 分类 | `world.read` |
| 显示名称 | Get Surface Blocks |
| 效果 | `WORLD_READ` |

## 作用

XZ 足迹每柱顶可见方块。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Heightmap Type | STRING |
| 入 | Exclude Air | BOOLEAN |
| 入 | Step | INTEGER |
| 入 | Max Columns | INTEGER |
| 出 | Surface Blocks | BLOCK_INFO_LIST |
| 出 | Surface Positions | BLOCK_LIST |
| 出 | Block Types | STRING_LIST |
| 出 | Count | INTEGER |
| 出 | Total Columns | INTEGER |
| 出 | Dominant Surface Block | STRING |
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
[上游].… ──► [Get Surface Blocks].Region
[Get Surface Blocks].Surface Blocks ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
