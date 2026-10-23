# Clear World Write Undo History

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.clear_undo_history` |
| 分类 | `world.write` |
| 显示名称 | Clear World Write Undo History |
| 效果 | `CONTEXT_WRITE` |

## 作用

清空当前玩家本世界的 write undo 历史。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger | BOOLEAN |
| 出 | Cleared Count | INTEGER |
| 出 | Remaining History | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |
| 出 | Status | STRING |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Clear World Write Undo History].Trigger
[Clear World Write Undo History].Cleared Count ──► [下游].…
```
