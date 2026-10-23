# Profile Triangulate 2D

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.triangulate_2d` |
| 分类 | `geometry.profiles` |
| 显示名称 | Profile Triangulate 2D |
| 效果 | `PURE` |

## 作用

耳切法把平面多边形轮廓拆成三角轮廓列表。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Profile | `input_profile` | POLYGON_PROFILE |
| 出 | Triangles | `output_triangles` | POLYGON_PROFILE_LIST |
| 出 | Triangle Count | `output_triangle_count` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Profile Triangulate 2D].Profile
[Profile Triangulate 2D].Triangles ──► [下游].…
```
