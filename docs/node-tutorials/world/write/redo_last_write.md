# Redo Last World Write

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.redo_last_write` |
| 分类 | `world.write` |
| 显示名称 | Redo Last World Write |
| 效果 | `WORLD_WRITE` |

## 作用

重做最近撤销的 world.write。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger | BOOLEAN |
| 出 | Success | BOOLEAN |
| 出 | Remaining Redo | INTEGER |
| 出 | Remaining History | INTEGER |
| 出 | Complete | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |
| 出 | Status | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Redo Last World Write].Trigger
[Redo Last World Write].Success ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
