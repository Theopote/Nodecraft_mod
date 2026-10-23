# List Length

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.list_length` |
| 分类 | `math.list` |
| 显示名称 | List Length |
| 效果 | `PURE` |

## 作用

返回列表长度。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [List Length].In
[List Length].Out ──► [下游].…
```
