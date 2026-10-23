# NURBS Curve

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.nurbs` |
| 分类 | `geometry.curves` |
| 显示名称 | NURBS Curve |
| 效果 | `PURE` |

## 作用

夹紧均匀 NURBS；可选与控制点对齐的 Weights。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Control Points / Weights / Degree / Resolution·Span | … | POINT_LIST / DOUBLE_LIST / … |
| 出 | Path / Points / Control Path / Control Count / Degree / Length | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [NURBS Curve].Control Points
[NURBS Curve].Path ──► [下游].…
```
