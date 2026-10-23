# Deconstruct Ellipsoid

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_ellipsoid` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Ellipsoid |
| 效果 | `PURE` |

## 作用

拆椭球中心、半径/直径向量、体积与近似表面积。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Ellipsoid | `input_ellipsoid` | ELLIPSOID_GEOMETRY |
| 出 | Center / Radii / Diameters | … | POINT / VECTOR / VECTOR |
| 出 | Volume / Surface Area | … | DOUBLE |
| 出 | Region / Bounding Box | … | REGION / BOUNDING_BOX |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Ellipsoid].Ellipsoid
[Deconstruct Ellipsoid].Center ──► [下游].…
```
