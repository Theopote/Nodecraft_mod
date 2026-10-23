# Dot Product

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.dot_product` |
| 分类 | `reference.vectors` |
| 显示名称 | Dot Product |
| 效果 | `PURE` |

## 作用

`A · B`（标量）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Vector A / B | `input_vector_a` / `input_vector_b` | VECTOR |
| 出 | Dot Product | `output_dot_product` | DOUBLE |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[A]+[B] ──► [Dot Product] ──► 判断夹角 / 投影长度
```
