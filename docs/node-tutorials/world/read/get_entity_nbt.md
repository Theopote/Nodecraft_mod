# Get Entity NBT

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_entity_nbt` |
| 分类 | `world.read` |
| 显示名称 | Get Entity NBT |
| 效果 | `WORLD_READ` |

## 作用

从实体对象读完整 NBT；查找用 Get Entity。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Entity | MINECRAFT_ENTITY |
| 入 | Max String Length | INTEGER |
| 出 | Found | BOOLEAN |
| 出 | Entity | MINECRAFT_ENTITY |
| 出 | NBT | NBT_COMPOUND |
| 出 | NBT String | STRING |
| 出 | Valid | BOOLEAN |
| 出 | NBT Size | INTEGER |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Entity NBT].Entity
[Get Entity NBT].Found ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
