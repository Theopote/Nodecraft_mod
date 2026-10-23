# Deconstruct Box Geometry

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_box` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Box Geometry |
| 效果 | `PURE` |

## 作用

从 BOX_GEOMETRY 拆出中心、半尺寸、角点/面列表与局部轴。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Box Geometry | `input_box_geometry` | BOX_GEOMETRY |
| 出 | Center / Half Extents | … | POINT / VECTOR |
| 出 | Is Oriented | `output_is_oriented` | BOOLEAN |
| 出 | Corners / Corner Names | … | POINT_LIST / STRING_LIST |
| 出 | Faces / Face Names | … | BOX_FACE_LIST / STRING_LIST |
| 出 | Corner / Face Count | … | INTEGER |
| 出 | X / Y / Z Axis | … | VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[Box].Box Geometry ──► [Deconstruct Box]
[Deconstruct].Faces ──► [Get Box Face] / 立面阵列
```
