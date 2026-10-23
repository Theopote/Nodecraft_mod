# Infinity Curve On Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.infinity_curve` |
| 分类 | `geometry.curves` |
| 显示名称 | Infinity Curve On Plane |
| 效果 | `PURE` |

## 作用

平面上 8 字形（类双纽线）采样曲线。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Size / Segments / Plane / X Axis | … | … |
| 出 | Path / Points | … | PATH / POINT_LIST |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Infinity Curve On Plane].Center
[Infinity Curve On Plane].Path ──► [下游].…
```
