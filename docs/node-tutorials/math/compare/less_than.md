# Less Than (<)

| 字段 | 值 |
|---|---|
| 节点 ID | `math.compare.less_than` |
| 分类 | `math.compare` |
| 显示名称 | Less Than (<) |
| 效果 | `PURE` |

## 作用

A 小于 B 时返回 true。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Less Than (<)].In
[Less Than (<)].Out ──► [下游].…
```
