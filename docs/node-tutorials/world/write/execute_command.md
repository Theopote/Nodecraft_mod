# Execute Command

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.execute_command` |
| 分类 | `world.write` |
| 显示名称 | Execute Command |
| 效果 | `WORLD_WRITE` |

## 作用

特权逃生舱：AllowCommandNodes 开启时执行命令。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Command | STRING |
| 入 | Executor | PLAYER |
| 入 | Trigger | BOOLEAN |
| 入 | Parse Result | BOOLEAN |
| 出 | Success | BOOLEAN |
| 出 | Result Code | INTEGER |
| 出 | Result Text | STRING |
| 出 | Parsed Result | ANY |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Execute Command].Command
[Execute Command].Success ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
