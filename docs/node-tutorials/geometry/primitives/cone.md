# Cone By Base Apex Radius

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.cone` |
| 分类 | `geometry.primitives` |
| 显示名称 | Cone By Base Apex Radius |
| 效果 | `PURE` |

## 作用

底面圆心 + 顶点 + 底半径构造圆锥。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Base Center / Apex | … | POINT |
| 入 | Base Radius | `input_radius` | DOUBLE |
| 出 | Cone / Geometry | … | CONE_GEOMETRY / GEOMETRY |
| 出 | Axis Path / Axis Vector / Height / Base Radius | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Cone By Base Apex Radius].Base Center
[Cone By Base Apex Radius].Cone ──► [下游].…
```
