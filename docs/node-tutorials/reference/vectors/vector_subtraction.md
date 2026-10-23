# Vector Subtraction (-)

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.vector_subtraction` |
| 分类 | `reference.vectors` |
| 显示名称 | Vector Subtraction (-) |
| 效果 | `PURE` |

## 作用

`A − B`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Vector A / B | … | VECTOR |
| 出 | Difference Vector | … | VECTOR |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Vector Subtraction (-)].Vector A
[Vector Subtraction (-)].Difference Vector ──► [下游].…
```
