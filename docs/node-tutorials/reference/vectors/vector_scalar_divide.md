# Vector Scalar Divide

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.vector_scalar_divide` |
| 分类 | `reference.vectors` |
| 显示名称 | Vector Scalar Divide |
| 效果 | `PURE` |

## 作用

`V / s`。除零失败。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Vector | … | VECTOR |
| 入 | Scalar | … | DOUBLE（除数） |
| 出 | Scaled Vector | … | VECTOR |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Vector Scalar Divide].Vector
[Vector Scalar Divide].Scaled Vector ──► [下游].…
```

## 注意事项

- Scalar = 0 → Valid false。
