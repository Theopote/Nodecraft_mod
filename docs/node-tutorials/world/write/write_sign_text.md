# Write Sign Text

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.write_sign_text` |
| 分类 | `world.write` |
| 显示名称 | Write Sign Text |
| 效果 | `WORLD_WRITE` |

## 作用

写告示牌正面文字。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 入 | Trigger | BOOLEAN |
| 入 | Line 1 | STRING |
| 入 | Line 2 | STRING |
| 入 | Line 3 | STRING |
| 入 | Line 4 | STRING |
| 入 | Lines List | STRING_LIST |
| 入 | Text Color | STRING |
| 入 | Glowing | BOOLEAN |
| 出 | Success | BOOLEAN |
| 出 | Is Sign | BOOLEAN |
| 出 | Sign Type | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Write Sign Text].Coordinate
[Write Sign Text].Success ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
