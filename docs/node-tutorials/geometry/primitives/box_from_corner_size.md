# Box by Corner + Size

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.box_from_corner_size` |
| 分类 | `geometry.primitives` |
| 显示名称 | Box by Corner + Size |
| 效果 | `PURE` |

## 作用

锚定一角 + **有符号** Size X/Y/Z；负值沿局部轴反向生长。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Corner | `input_corner` | POINT |
| 入 | Plane | `input_plane` | PLANE（可选） |
| 入 | Size X/Y/Z | … | DOUBLE（非零，可负） |
| 入 | Rotation X/Y/Z | … | DOUBLE（度） |
| 出 | Geometry | `output_geometry` | GEOMETRY |
| 出 | Box Geometry | `output_box_geometry` | BOX_GEOMETRY |
| 出 | Corners | `output_corners` | POINT_LIST（8 角） |
| 出 | Faces | `output_faces` | BOX_FACE_LIST（6 面） |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Box by Corner + Size].Corner
[Box by Corner + Size].Geometry ──► [下游].…
```

## 注意事项

- Size 不可为 0；符号决定从角点朝哪边长出。
