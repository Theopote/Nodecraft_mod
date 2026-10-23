# Square Pyramid

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.square_pyramid` |
| 分类 | `geometry.primitives` |
| 显示名称 | Square Pyramid |
| 效果 | `PURE` |

## 作用

正方形底 + 高构造四棱锥；Plane 定底面，可选 X Axis 旋转底边。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Base Center | `input_center` | POINT |
| 入 | Base Size / Height | … | DOUBLE |
| 入 | Plane | `input_plane` | PLANE |
| 入 | X Axis | `input_x_axis` | VECTOR（可选） |
| 出 | Geometry | `output_geometry` | GEOMETRY |
| 出 | Apex / Base Points / Base Size / Height / Plane | … | POINT / POINT_LIST / … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Square Pyramid].Base Center
[Square Pyramid].Geometry ──► [下游].…
```
