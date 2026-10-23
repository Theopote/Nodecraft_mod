# Deconstruct Box Face

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.deconstruct_face` |
| 分类 | `reference.points` |
| 显示名称 | Deconstruct Box Face |
| 效果 | `PURE` |

## 作用

拆 BOX_FACE：名称、索引、角点列、中心、法线、平面、边列表、父盒角点索引。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Face | `input_face` | BOX_FACE |
| 出 | Name / Index | `output_name` / `output_index` | STRING / INTEGER |
| 出 | Corners | `output_corners` | POINT_LIST |
| 出 | Center / Normal / Plane | … | POINT / VECTOR / PLANE |
| 出 | Edges | `output_edges` | LINE_LIST |
| 出 | Corner Indices | `output_corner_indices` | INTEGER_LIST |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[Get Box Face].Face ──► [Deconstruct Box Face].Plane ──► Profiles
```
