# Pilaster / Cornice

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.pilaster_cornice` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Pilaster / Cornice |
| 效果 | `PURE` |

## 作用

沿盒体面生成壁柱 + 可选檐口带。用 Include Side Pilasters / Include Cornice 开关部件。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Pilaster Width·Depth·Height / Cornice Height / Margin / Include* | … |
| 出 | Geometry / Count | GEOMETRY / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Pilaster / Cornice].Face
[Pilaster / Cornice].Geometry ──► [下游].…
```
