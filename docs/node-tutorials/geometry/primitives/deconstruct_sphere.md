# Deconstruct Sphere

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_sphere` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Sphere |
| 效果 | `PURE` |

## 作用

拆球：中心、半径、直径、表面积、体积、Region、Bounding Box。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Sphere | `input_sphere` | SPHERE |
| 出 | Center / Radius / Diameter | … | POINT / DOUBLE / DOUBLE |
| 出 | Surface Area / Volume | … | DOUBLE |
| 出 | Region / Bounding Box | … | REGION / BOUNDING_BOX |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Sphere].Sphere
[Deconstruct Sphere].Center ──► [下游].…
```
