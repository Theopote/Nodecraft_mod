# Remove Variable

| 字段 | 值 |
|---|---|
| 节点 ID | `variable.remove` |
| 分类 | `variable` |
| 显示名称 | Remove Variable |
| 效果 | `CONTEXT_WRITE` |

## 作用

从作用域删除用户变量。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Name | STRING |
| 出 | Previous | ANY |
| 出 | Name | STRING |
| 出 | Removed | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Remove Variable].Name
[Remove Variable].Previous ──► [下游].…
```
