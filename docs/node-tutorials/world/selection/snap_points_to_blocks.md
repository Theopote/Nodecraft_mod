# Snap Point List To Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.snap_points_to_blocks` |
| 分类 | `world.selection` |
| 显示名称 | Snap Point List To Blocks |
| 效果 | `PURE` |

## 作用

POINT_LIST 贴格；任一坏成员整节点失败。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points | POINT_LIST |
| 出 | Blocks | BLOCK_LIST |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Snap Point List To Blocks].Points
[Snap Point List To Blocks].Blocks ──► [下游].…
```
