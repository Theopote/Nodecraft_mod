# Signal Fork

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.assist.signal_fork` |
| 分类 | `utilities.assist` |
| 显示名称 | Signal Fork |
| 效果 | `PURE` |

## 作用

一路输入恒等分到多路输出，便于布局分叉。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Signal Fork].In
[Signal Fork].Out ──► [下游].…
```
