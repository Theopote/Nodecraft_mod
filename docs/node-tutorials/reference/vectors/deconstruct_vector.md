# Deconstruct Vector

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.deconstruct_vector` |
| 分类 | `reference.vectors` |
| 显示名称 | Deconstruct Vector |
| 效果 | `PURE` |

## 作用

拆 VECTOR 为 X/Y/Z（DOUBLE）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Vector | `input_vector` | VECTOR |
| 出 | X / Y / Z | `output_x` 等 | DOUBLE |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Deconstruct Vector].Vector
[Deconstruct Vector].X ──► [下游].…
```
