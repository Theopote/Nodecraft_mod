# Box by Two Corners

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.box_from_corners` |
| 分类 | `geometry.primitives` |
| 显示名称 | Box by Two Corners |
| 效果 | `PURE` |

## 作用

两对角点生成**轴对齐**盒（世界轴）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Corner A / B | `input_corner_a` / `input_corner_b` | POINT |
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
[上游].… ──► [Box by Two Corners].Corner A
[Box by Two Corners].Geometry ──► [下游].…
```

## 注意事项

- 各轴跨度须 > 0（不能共面/重合）。结果始终轴对齐。
