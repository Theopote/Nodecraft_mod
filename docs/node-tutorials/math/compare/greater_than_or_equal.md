# Greater Than or Equal (>=)

| 字段 | 值 |
|---|---|
| 节点 ID | `math.compare.greater_than_or_equal` |
| 分类 | `math.compare` |
| 显示名称 | Greater Than or Equal (>=) |
| 效果 | `PURE` |

## 作用

A 大于等于 B 时返回 true。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Greater Than or Equal (>=)].In
[Greater Than or Equal (>=)].Out ──► [下游].…
```
