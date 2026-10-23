# Deconstruct Frustum Cone

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_frustum_cone` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Frustum Cone |
| 效果 | `PURE` |

## 作用

拆圆台：底/顶中心、轴、高、两半径、斜高、面积、体积、包围。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Frustum | `input_frustum` | FRUSTUM_CONE_GEOMETRY |
| 出 | Base / Top Center · Axis Path / Vector · Height | … | … |
| 出 | Base / Top Radius · Slant Height · 面积 / Volume | … | DOUBLE |
| 出 | Region / Bounding Box | … | REGION / BOUNDING_BOX |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Frustum Cone].Frustum
[Deconstruct Frustum Cone].Base ──► [下游].…
```
