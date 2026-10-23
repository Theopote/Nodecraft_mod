# Number Series

| 字段 | 值 |
|---|---|
| 节点 ID | `math.sequence.series` |
| 分类 | `math.sequence` |
| 显示名称 | Number Series |
| 效果 | `PURE` |

## 作用

按 Start / Step / Count 生成 DOUBLE_LIST。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Number Series].In
[Number Series].Out ──► [下游].…
```
