# Set Block NBT

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.set_block_nbt` |
| 分类 | `world.write` |
| 显示名称 | Set Block NBT |
| 效果 | `WORLD_WRITE` |

## 作用

写入/合并目标格方块实体 NBT。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 入 | Trigger | BOOLEAN |
| 入 | NBT | NBT_COMPOUND |
| 入 | NBT String | STRING |
| 入 | Merge | BOOLEAN |
| 入 | Notify | BOOLEAN |
| 出 | Success | BOOLEAN |
| 出 | Has Block Entity | BOOLEAN |
| 出 | Applied NBT | NBT_COMPOUND |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Set Block NBT].Coordinate
[Set Block NBT].Success ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
