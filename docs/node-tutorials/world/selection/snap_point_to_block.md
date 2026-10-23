# Snap Point To Block

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.snap_point_to_block` |
| 分类 | `world.selection` |
| 显示名称 | Snap Point To Block |
| 效果 | `PURE` |

## 作用

点贴格：包含格或最近格心。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Point | POINT |
| 出 | Block Pos | BLOCK_POS |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |
| 出 | Distance | DOUBLE |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Snap Point To Block].Point
[Snap Point To Block].Block Pos ──► [下游].…
```
