# Get Biome

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_biome` |
| 分类 | `world.read` |
| 显示名称 | Get Biome |
| 效果 | `WORLD_READ` |

## 作用

读生物群系 ID 与基础温度。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 出 | Biome | BIOME |
| 出 | Biome Name | STRING |
| 出 | Temperature | FLOAT |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Biome].Coordinate
[Get Biome].Biome ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
