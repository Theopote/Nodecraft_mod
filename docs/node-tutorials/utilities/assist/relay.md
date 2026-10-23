# Relay

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.assist.relay` |
| 分类 | `utilities.assist` |
| 显示名称 | Relay |
| 效果 | `PURE` |

## 作用

信号透传，可选视觉语义标签。

## 端点

| （见面板 / 动态端口） |||

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Relay].In
[Relay].Out ──► [下游].…
```
