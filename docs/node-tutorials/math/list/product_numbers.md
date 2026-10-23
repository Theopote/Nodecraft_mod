# Product Numbers

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.product_numbers` |
| 分类 | `math.list` |
| 显示名称 | Product Numbers |
| 效果 | `PURE` |

## 作用

对 DOUBLE_LIST 连乘。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Numbers | DOUBLE_LIST |
| 出 | Product | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Product Numbers].Numbers
[Product Numbers].Product ──► [下游].…
```
