# B-Spline

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.b_spline` |
| 分类 | `geometry.curves` |
| 显示名称 | B-Spline |
| 效果 | `PURE` |

## 作用

夹紧均匀 B 样条。Degree 1..5 且 < 控制点数。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Control Points / Degree / Resolution·Span | … | … |
| 出 | Path / Points / Control Path / Control Count / Degree / Length | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [B-Spline].Control Points
[B-Spline].Path ──► [下游].…
```
