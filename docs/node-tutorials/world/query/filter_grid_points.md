# Filter Grid Points

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.filter_grid_points` |
| 分类 | `world.query` |
| 显示名称 | Filter Grid Points |
| 效果 | `PURE` |

## 作用

点列拆成格心点 / 离格点（不贴格）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points | POINT_LIST |
| 出 | Grid Points | POINT_LIST |
| 出 | Grid Blocks | BLOCK_LIST |
| 出 | Off-Grid Points | POINT_LIST |
| 出 | Grid Count | INTEGER |
| 出 | Off-Grid Count | INTEGER |
| 出 | Valid Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Filter Grid Points].Points
[Filter Grid Points].Grid Points ──► [下游].…
```
