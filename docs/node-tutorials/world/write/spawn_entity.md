# Spawn Entity

| 字段 | 值 |
|---|---|
| 节点 ID | `world.write.spawn_entity` |
| 分类 | `world.write` |
| 显示名称 | Spawn Entity |
| 效果 | `WORLD_WRITE` |

## 作用

在 POINT 生成实体。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Position | POINT |
| 入 | Entity Type | ENTITY_TYPE |
| 入 | Trigger | BOOLEAN |
| 入 | NBT Data | NBT |
| 入 | Motion | VECTOR |
| 入 | No AI | BOOLEAN |
| 入 | Invulnerable | BOOLEAN |
| 入 | Persistent | BOOLEAN |
| 入 | Yaw | DOUBLE |
| 入 | Pitch | DOUBLE |
| 出 | Entity | MINECRAFT_ENTITY |
| 出 | Success | BOOLEAN |
| 出 | Entity UUID | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Spawn Entity].Position
[Spawn Entity].Entity ──► [下游].…
```

## 注意事项

- 会改世界；可用本分类 Undo/Redo 或统一历史（视操作路径）。
