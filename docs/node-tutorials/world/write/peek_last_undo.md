# Peek Last World Write Undo

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.peek_last_undo` |
| 分类 | `world.write` |
| 显示名称 | Peek Last World Write Undo |
| 效果 | `CONTEXT_READ` |

## 作用

查看最近 undo 记录（影响数/范围）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | Has Record | BOOLEAN |
| 出 | Recorded Count | INTEGER |
| 出 | Remaining History | INTEGER |
| 出 | Min Position | BLOCK_POS |
| 出 | Max Position | BLOCK_POS |
| 出 | Affected Region | REGION |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Peek Last World Write Undo].In
[Peek Last World Write Undo].Has Record ──► [下游].…
```
