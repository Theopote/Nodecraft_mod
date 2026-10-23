# Get Light Level

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.get_light_level` |
| 分类 | `world.query` |
| 显示名称 | Get Light Level |
| 效果 | `WORLD_READ` |

## 作用

读取合并/天空/方块光照。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 出 | Light Level | INTEGER |
| 出 | Sky Light | INTEGER |
| 出 | Block Light | INTEGER |
| 出 | Is Day | BOOLEAN |
| 出 | Can See Sky | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Light Level].Coordinate
[Get Light Level].Light Level ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
