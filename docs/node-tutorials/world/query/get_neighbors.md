# Get Neighbor Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.get_neighbors` |
| 分类 | `world.query` |
| 显示名称 | Get Neighbor Blocks |
| 效果 | `WORLD_READ` |

## 作用

中心周围轴邻或立方体邻域方块。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Center | BLOCK_POS |
| 入 | Include Diagonals | BOOLEAN |
| 入 | Radius | INTEGER |
| 出 | Coordinates | BLOCK_LIST |
| 出 | Block IDs | STRING_LIST |
| 出 | Block Infos | LIST |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Neighbor Blocks].Center
[Get Neighbor Blocks].Coordinates ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
