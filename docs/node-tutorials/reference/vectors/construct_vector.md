# Construct Vector

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.construct_vector` |
| 分类 | `reference.vectors` |
| 显示名称 | Construct Vector |
| 效果 | `PURE` |

## 作用

由 DOUBLE 的 X/Y/Z 构造 VECTOR。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | X / Y / Z | `input_x` 等 | DOUBLE |
| 出 | Vector | `output_vector` | VECTOR |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Construct Vector].X
[Construct Vector].Vector ──► [下游].…
```
