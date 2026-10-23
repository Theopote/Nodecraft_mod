# Point In Region

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.is_point_in_region` |
| 分类 | `world.query` |
| 显示名称 | Point In Region |
| 效果 | `PURE` |

## 作用

方块格中心是否落在 Region 内。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Point | BLOCK_POS |
| 入 | Region | REGION |
| 出 | Is Inside | BOOLEAN |
| 出 | Distance | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Point In Region].Point
[Point In Region].Is Inside ──► [下游].…
```
