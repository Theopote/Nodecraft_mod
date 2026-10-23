# Normalize Vector

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.normalize_vector` |
| 分类 | `reference.vectors` |
| 显示名称 | Normalize Vector |
| 效果 | `PURE` |

## 作用

归一化为单位向量。零向量失败。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Vector | `input_vector` | VECTOR |
| 出 | Normalized | `output_normalized_vector` | VECTOR |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Normalize Vector].Vector
[Normalize Vector].Normalized ──► [下游].…
```

## 注意事项

- 非零且可归一化才 Valid。
