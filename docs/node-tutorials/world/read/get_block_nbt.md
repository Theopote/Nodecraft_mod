# Get Block NBT

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_block_nbt` |
| 分类 | `world.read` |
| 显示名称 | Get Block NBT |
| 效果 | `WORLD_READ` |

## 作用

读方块实体完整 NBT（Max String Length 只限序列化输出）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 入 | Max String Length | INTEGER |
| 出 | Has Block Entity | BOOLEAN |
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
[上游].… ──► [Get Block NBT].Coordinate
[Get Block NBT].Has Block Entity ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
