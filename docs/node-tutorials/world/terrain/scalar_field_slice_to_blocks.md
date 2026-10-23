# Scalar Field Slice To Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.scalar_field_slice_to_blocks` |
| 分类 | `world.terrain` |
| 显示名称 | Scalar Field Slice To Blocks |
| 效果 | `PURE` |

## 作用

水平切片可视化标量场（低/高阈值方块）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Scalar Field | SCALAR_FIELD |
| 入 | Slice Y | INTEGER |
| 入 | Threshold | DOUBLE |
| 入 | Low Block | BLOCK_TYPE |
| 入 | High Block | BLOCK_TYPE |
| 入 | Step | INTEGER |
| 入 | Only High Blocks | BOOLEAN |
| 入 | Max Placements | INTEGER |
| 出 | Block Placements | BLOCK_PLACEMENT_LIST |
| 出 | Points | BLOCK_LIST |
| 出 | Count | INTEGER |
| 出 | Step Used | INTEGER |
| 出 | Hit Limit | BOOLEAN |
| 出 | Complete | BOOLEAN |
| 出 | Stopped Reason | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Scalar Field Slice To Blocks].Region
[Scalar Field Slice To Blocks].Block Placements ──► [下游].…
```
