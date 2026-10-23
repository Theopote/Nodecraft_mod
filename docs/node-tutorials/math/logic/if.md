# If

| 字段 | 值 |
|---|---|
| 节点 ID | `math.logic.if` |
| 分类 | `math.logic` |
| 显示名称 | If |
| 效果 | `PURE` |

## 作用

Condition 为真取 True Value，否则 False Value。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Condition | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [If].Condition
[If].Out ──► [下游].…
```
