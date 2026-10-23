# Array Along Curve

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.array_along_curve` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Array Along Curve |
| 效果 | `PURE` |

## 作用

沿 PATH（或兜底 Path Points）重复放置 `box` / `cylinder` 构件。Count 与 Spacing 二选一驱动间距。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Path / Path Points / Count / Spacing / Element Type / Width·Height·Depth / Up Vector | … |
| 出 | Geometry / Count | GEOMETRY / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Array Along Curve].Path
[Array Along Curve].Geometry ──► [下游].…
```
