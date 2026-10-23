# Multi-Region Selection

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.multi_region` |
| 分类 | `world.selection` |
| 显示名称 | Multi-Region Selection |
| 效果 | `PURE` |

## 作用

聚合多 Region / min·max 对为列表与总包围。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Regions | REGION_LIST |
| 入 | Region A | REGION |
| 入 | Region B | REGION |
| 入 | Region C | REGION |
| 入 | Min Blocks | BLOCK_LIST |
| 入 | Max Blocks | BLOCK_LIST |
| 出 | Regions | REGION_LIST |
| 出 | Count | INTEGER |
| 出 | Bounds Region | REGION |
| 出 | Bounds Min Block | BLOCK_POS |
| 出 | Bounds Max Block | BLOCK_POS |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Multi-Region Selection].Regions
[Multi-Region Selection].Regions ──► [下游].…
```
