# Filter Points By Rule

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.filter_points_by_rule` |
| 分类 | `world.query` |
| 显示名称 | Filter Points By Rule |
| 效果 | `PURE` |

## 作用

按高度与可选坡度过滤点集。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points | POINT_LIST |
| 入 | Normals | VECTOR_LIST |
| 入 | Min Height | DOUBLE |
| 入 | Max Height | DOUBLE |
| 入 | Min Slope | DOUBLE |
| 入 | Max Slope | DOUBLE |
| 入 | Invert | BOOLEAN |
| 出 | Filtered Points | POINT_LIST |
| 出 | Removed Points | POINT_LIST |
| 出 | Mask | BOOLEAN_LIST |
| 出 | Slopes | DOUBLE_LIST |
| 出 | Count | INTEGER |
| 出 | Total Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Filter Points By Rule].Points
[Filter Points By Rule].Filtered Points ──► [下游].…
```
