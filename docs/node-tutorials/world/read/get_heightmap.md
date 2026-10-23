# Get Heightmap

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_heightmap` |
| 分类 | `world.read` |
| 显示名称 | Get Heightmap |
| 效果 | `WORLD_READ` |

## 作用

XZ 足迹每柱顶 Y（忽略 Region Y）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Heightmap Type | STRING |
| 入 | Step | INTEGER |
| 入 | Max Columns | INTEGER |
| 出 | Surface Points | BLOCK_LIST |
| 出 | Height Values | INTEGER_LIST |
| 出 | Column Count | INTEGER |
| 出 | Resolved Heightmap Type | STRING |
| 出 | Min Height | INTEGER |
| 出 | Max Height | INTEGER |
| 出 | Average Height | DOUBLE |
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
[上游].… ──► [Get Heightmap].Region
[Get Heightmap].Surface Points ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
