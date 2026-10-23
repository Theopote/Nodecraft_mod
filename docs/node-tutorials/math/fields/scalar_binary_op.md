# Combine Scalar Fields

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.scalar_binary_op` |
| 分类 | `math.fields` |
| 显示名称 | Combine Scalar Fields |
| 效果 | `PURE` |

## 作用

两标量场做基本算术组合。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | A | SCALAR_FIELD |
| 入 | B | SCALAR_FIELD |
| 出 | Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Combine Scalar Fields].A
[Combine Scalar Fields].Field ──► [下游].…
```
