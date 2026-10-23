# Convex Hull 3D From Points

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.analysis.convex_hull_3d` |
| 分类 | `geometry.analysis` |
| 显示名称 | Convex Hull 3D From Points |
| 效果 | `PURE` |

## 作用

点云 → 三维凸包三角网格。暴力枚举，**适合小点集**；共面/共线可能无三角面。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points | POINT_LIST |
| 出 | Mesh / Hull Vertices / Triangle Count | TRIANGLE_MESH / POINT_LIST / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Convex Hull 3D From Points].Points
[Convex Hull 3D From Points].Mesh ──► [下游].…
```
