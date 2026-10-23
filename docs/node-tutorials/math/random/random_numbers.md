# Random Numbers

| 字段 | 值 |
|---|---|
| 节点 ID | `math.random.random_numbers` |
| 分类 | `math.random` |
| 显示名称 | Random Numbers |
| 效果 | `PURE` |

## 作用

域内确定性随机 double 列表。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Domain | NUMERIC_RANGE |
| 入 | Count | INTEGER |
| 入 | Seed | INTEGER |
| 出 | Values | DOUBLE_LIST |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Random Numbers].Domain
[Random Numbers].Values ──► [下游].…
```
