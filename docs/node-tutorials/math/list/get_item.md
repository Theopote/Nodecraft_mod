# Get Item

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.get_item` |
| 分类 | `math.list` |
| 显示名称 | Get Item |
| 效果 | `PURE` |

## 作用

按索引取列表项。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Get Item].In
[Get Item].Out ──► [下游].…
```
