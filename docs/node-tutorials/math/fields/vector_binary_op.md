# Combine Vector Fields

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.vector_binary_op` |
| 分类 | `math.fields` |
| 显示名称 | Combine Vector Fields |
| 效果 | `PURE` |

## 作用

两向量场逐分量运算或叉积。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | A | VECTOR_FIELD |
| 入 | B | VECTOR_FIELD |
| 出 | Field | VECTOR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Combine Vector Fields].A
[Combine Vector Fields].Field ──► [下游].…
```
