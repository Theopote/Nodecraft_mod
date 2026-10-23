# Random Number

| 字段 | 值 |
|---|---|
| 节点 ID | `math.random.random_number` |
| 分类 | `math.random` |
| 显示名称 | Random Number |
| 效果 | `PURE` |

## 作用

域内单个确定性随机 double。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Domain | NUMERIC_RANGE |
| 入 | Seed | INTEGER |
| 出 | Random | DOUBLE |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Random Number].Domain
[Random Number].Random ──► [下游].…
```
