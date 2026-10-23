# Repeat Item

| 字段 | 值 |
|---|---|
| 节点 ID | `math.sequence.repeat` |
| 分类 | `math.sequence` |
| 显示名称 | Repeat Item |
| 效果 | `PURE` |

## 作用

把单项重复 Count 次成 LIST（列表项整体重复，不展开铺砖）。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Repeat Item].In
[Repeat Item].Out ──► [下游].…
```
