# Get Entity

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.get_entity` |
| 分类 | `world.query` |
| 显示名称 | Get Entity |
| 效果 | `WORLD_READ` |

## 作用

按 UUID 或玩家附近最近类型查找实体。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | UUID | STRING |
| 入 | Entity Type | ENTITY_TYPE |
| 入 | Max Distance | DOUBLE |
| 出 | Entity | MINECRAFT_ENTITY |
| 出 | Entity Type | ENTITY_TYPE |
| 出 | Entity Position | POINT |
| 出 | Found | BOOLEAN |
| 出 | Distance | DOUBLE |
| 出 | UUID | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Entity].UUID
[Get Entity].Entity ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
