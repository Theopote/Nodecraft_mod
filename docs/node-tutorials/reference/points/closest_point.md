# Closest Point

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.closest_point` |
| 分类 | `reference.points` |
| 显示名称 | Closest Point |
| 效果 | `PURE` |

## 作用

在点列中找距参考点最近的点；等距时保留**最小下标**。输出连续 POINT（不吸附格点）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Point | `input_point` | POINT |
| 入 | Points | `input_coordinates` | POINT_LIST |
| 出 | Closest Point | `output_closest_point` | POINT |
| 出 | Distance | `output_distance` | DOUBLE |
| 出 | Index | `output_index` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Closest Point].Point
[Closest Point].Closest Point ──► [下游].…
```
