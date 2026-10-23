# Undo Last Change

| 字段 | 值 |
|---|---|
| 节点 ID | `output.execute.undo_last_bake` |
| 分类 | `output.execute` |
| 显示名称 | Undo Last Change |
| 效果 | `WORLD_WRITE` |

## 作用

从统一历史栈撤销最近一次世界写入（Apply Changes 或 world.write）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger | EXEC |
| 出 | Success / Restored Count / Remaining History / Status / Task ID | … |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Undo Last Change].Trigger
[Undo Last Change].Success ──► [下游].…
```
