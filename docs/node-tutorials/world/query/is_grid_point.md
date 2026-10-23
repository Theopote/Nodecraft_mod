# Is Grid Point

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.is_grid_point` |
| 分类 | `world.query` |
| 显示名称 | Is Grid Point |
| 效果 | `PURE` |

## 作用

判断点是否落在方块格心（不贴格）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Point | POINT |
| 出 | Is Grid Point | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Nearest Coordinate | BLOCK_POS |
| 出 | Offset Vector | VECTOR |
| 出 | Distance | DOUBLE |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Is Grid Point].Point
[Is Grid Point].Is Grid Point ──► [下游].…
```
