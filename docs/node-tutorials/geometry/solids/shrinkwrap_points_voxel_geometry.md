# Shrinkwrap Points To Voxel Geometry

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.shrinkwrap_points_voxel_geometry` |
| 分类 | `geometry.solids` |
| 显示名称 | Shrinkwrap Points To Voxel Geometry |
| 效果 | `PURE` |

## 作用

先体素化几何，再把点吸附到最近体素块中心（与三角条带 shrinkwrap 不同）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points / Geometry | … | POINT_LIST / GEOMETRY |
| 出 | Projected Points / Distances | … | POINT_LIST / DOUBLE_LIST |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Shrinkwrap Points To Voxel Geometry].Points
[Shrinkwrap Points To Voxel Geometry].Projected Points ──► [下游].…
```
