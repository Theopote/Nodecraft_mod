# Mid Point

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.mid_point` |
| 分类 | `reference.points` |
| 显示名称 | Mid Point |
| 效果 | `PURE` |

## 作用

两点中点。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Point A / B | `input_point_a` / `input_point_b` | POINT |
| 出 | Mid Point | `output_midpoint` | POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Mid Point].Point A
[Mid Point].Mid Point ──► [下游].…
```
