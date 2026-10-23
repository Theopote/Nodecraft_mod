# Deconstruct Point

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.deconstruct_point` |
| 分类 | `reference.points` |
| 显示名称 | Deconstruct Point |
| 效果 | `PURE` |

## 作用

把 `POINT` 拆成 DOUBLE 的 X/Y/Z。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Point | `input_point` | POINT |
| 出 | X / Y / Z | `output_x` 等 | DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Deconstruct Point].Point
[Deconstruct Point].X ──► [下游].…
```
