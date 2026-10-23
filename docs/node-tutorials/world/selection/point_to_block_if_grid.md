# Point To Block If Grid

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.point_to_block_if_grid` |
| 分类 | `world.selection` |
| 显示名称 | Point To Block If Grid |
| 效果 | `PURE` |

## 作用

仅当点在格心容差内才输出方块格。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Point | POINT |
| 出 | Coordinate | BLOCK_POS |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |
| 出 | Is Grid Point | BOOLEAN |
| 出 | Nearest Coordinate | BLOCK_POS |
| 出 | Distance | DOUBLE |
| 出 | Offset Vector | VECTOR |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Point To Block If Grid].Point
[Point To Block If Grid].Coordinate ──► [下游].…
```
