# Roof Generator

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.roof_generator` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Roof Generator |
| 效果 | `PURE` |

## 作用

特种屋顶便利节点：`asymmetric_gable` / `hip` / `cross_gable` / `m`。平/单坡/双坡请用 [Roof Base](./roof_base.md)。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Roof Type / Height / Thickness / Overhang / Ridge* / 各 Ratio·Offset | … |
| 出 | Geometry / Eave·Ridge·Valley 路径列表 / Faces / Slope Directions | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Roof Generator].Face
[Roof Generator].Geometry ──► [下游].…
```
