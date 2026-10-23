# Heart On Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.heart_profile` |
| 分类 | `geometry.profiles` |
| 显示名称 | Heart On Plane |
| 效果 | `PURE` |

## 作用

Heart On Plane：在平面上构造闭合多边形轮廓。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Width / Height / Segments | … | … |
| 入 | Plane / X Axis | … | PLANE / VECTOR |
| 出 | Points | `output_points` | POINT_LIST（闭合） |
| 出 | Profile | `output_profile` | POLYGON_PROFILE |
| 出 | Boundary | `output_boundary` | PATH |
| 出 | Plane / Center | `output_plane` / `output_center` | PLANE / POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Heart On Plane].Center
[Heart On Plane].Points ──► [下游].…
```

## 注意事项

- Plane 未接时默认 **XZ（水平）**；Center 未接时用 Plane 原点。
- 可选轴/Start Direction 须在平面内（投影后使用）。
- 曲线类轮廓为多边形近似，Segments 越大越圆滑。
