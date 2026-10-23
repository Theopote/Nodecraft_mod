# Print To Chat

| 字段 | 值 |
|---|---|
| 节点 ID | `output.debug.print_to_chat` |
| 分类 | `output.debug` |
| 显示名称 | Print To Chat |
| 效果 | `UI_EFFECT` |

## 作用

EXEC 触发时把连接值打印到 Minecraft 聊天。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Data / Prefix / Color / Trigger | ANY / STRING / EXEC |
| 出 | Success / Message / Error | BOOLEAN / STRING |

## 使用步骤

1. 按端点表连接输入。
2. 查看输出并接到下游。

## 典型接法

```text
[上游].… ──► [Print To Chat].Data
[Print To Chat].Success ──► [下游].…
```
