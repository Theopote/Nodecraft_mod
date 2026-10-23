# Clear Variables

| 字段 | 值 |
|---|---|
| 节点 ID | `variable.clear` |
| 分类 | `variable` |
| 显示名称 | Clear Variables |
| 效果 | `CONTEXT_WRITE` |

## 作用

清空用户变量（保留内部 NodeCraft 变量）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Clear | BOOLEAN |
| 出 | Cleared Count | INTEGER |
| 出 | Cleared | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Clear Variables].Clear
[Clear Variables].Cleared Count ──► [下游].…
```
