# Get Block

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_block` |
| 分类 | `world.read` |
| 显示名称 | Get Block |
| 效果 | `WORLD_READ` |

## 作用

读单格状态、类型、光、流体、方块实体。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 出 | Block State | BLOCK_INFO |
| 出 | Block Type | BLOCK_TYPE |
| 出 | Is Air | BOOLEAN |
| 出 | Is Solid | BOOLEAN |
| 出 | Light Level | INTEGER |
| 出 | Has Block Entity | BOOLEAN |
| 出 | Fluid Type | STRING |
| 出 | Has Fluid | BOOLEAN |
| 出 | Is Replaceable | BOOLEAN |
| 出 | Luminance | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Block].Coordinate
[Get Block].Block State ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
