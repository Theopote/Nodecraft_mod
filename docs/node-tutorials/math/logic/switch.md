# Switch

| 字段 | 值 |
|---|---|
| 节点 ID | `math.logic.switch` |
| 分类 | `math.logic` |
| 显示名称 | Switch |
| 效果 | `PURE` |

## 作用

按索引选多路输入之一，带默认回退。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Index | INTEGER |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Switch].Index
[Switch].Out ──► [下游].…
```
