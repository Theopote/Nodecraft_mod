# Lerp Vectors

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.lerp_vectors` |
| 分类 | `reference.vectors` |
| 显示名称 | Lerp Vectors |
| 效果 | `PURE` |

## 作用

线性插值：`(1−T)·A + T·B`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | A / B / T | … | VECTOR / VECTOR / DOUBLE |
| 出 | Result | … | VECTOR |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Lerp Vectors].A
[Lerp Vectors].Result ──► [下游].…
```
