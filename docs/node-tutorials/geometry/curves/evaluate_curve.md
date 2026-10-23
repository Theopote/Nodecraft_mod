# Evaluate Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.evaluate_curve` |
| 分类 | `geometry.curves` |
| 显示名称 | Evaluate Path |
| 效果 | `PURE` |

## 作用

按归一化弧长 t∈[0,1] 求点与单位切线。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / t | … | PATH / DOUBLE |
| 出 | Point / Tangent / Length | … | POINT / VECTOR / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Evaluate Path].Path
[Evaluate Path].Point ──► [下游].…
```
