# Voronoi Cells 2D On Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.voronoi_cells_plane` |
| 分类 | `geometry.profiles` |
| 显示名称 | Voronoi Cells 2D On Plane |
| 效果 | `PURE` |

## 作用

站点投影到平面，建裁剪 Voronoi（JTS），每格一多边形轮廓。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Sites / Plane | … | POINT_LIST / PLANE |
| 出 | Cells | `output_cells` | POLYGON_PROFILE_LIST |
| 出 | Cell Count | `output_cell_count` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Voronoi Cells 2D On Plane].Sites
[Voronoi Cells 2D On Plane].Cells ──► [下游].…
```
