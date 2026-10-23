# Logarithm (log)

| 字段 | 值 |
|---|---|
| 节点 ID | `math.scalar_math.logarithm` |
| 分类 | `math.scalar_math` |
| 显示名称 | Logarithm (log) |
| 效果 | `PURE` |

## 作用

以 Base 为底的 Number 对数。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Number | DOUBLE |
| 入 | Base | DOUBLE |
| 出 | Logarithm | DOUBLE |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Logarithm (log)].Number
[Logarithm (log)].Logarithm ──► [下游].…
```
