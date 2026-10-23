# Get Fluid Level

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.get_fluid_level` |
| 分类 | `world.query` |
| 显示名称 | Get Fluid Level |
| 效果 | `WORLD_READ` |

## 作用

读取流体类型与液位。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 出 | Fluid Level | INTEGER |
| 出 | Fluid Type | STRING |
| 出 | Is Source | BOOLEAN |
| 出 | Is Flowing | BOOLEAN |
| 出 | Is Water | BOOLEAN |
| 出 | Is Lava | BOOLEAN |
| 出 | Has Fluid | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Fluid Level].Coordinate
[Get Fluid Level].Fluid Level ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
