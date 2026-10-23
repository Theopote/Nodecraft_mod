# Deconstruct Cylinder

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_cylinder` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Cylinder |
| 效果 | `PURE` |

## 作用

拆圆柱轴、半径、解析面积/体积与包围。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Cylinder | `input_cylinder` | CYLINDER_GEOMETRY |
| 出 | Start / End / Axis Path / Axis Vector | … | POINT / POINT / PATH / VECTOR |
| 出 | Height / Radius / Diameter | … | DOUBLE |
| 出 | Base / Lateral / Surface Area / Volume | … | DOUBLE |
| 出 | Region / Bounding Box | … | REGION / BOUNDING_BOX |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Cylinder].Cylinder
[Deconstruct Cylinder].Start ──► [下游].…
```
