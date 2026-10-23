# Instance Block Placements

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.linear.instance_block_placements` |
| 分类 | `pattern.linear` |
| 显示名称 | Instance Block Placements |
| 效果 | `PURE` |

## 作用

在每个锚点方块处实例化模板 `BLOCK_PLACEMENT_LIST`（相对 Template Origin）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Anchors / Template Placements / Template Origin / Enabled | BLOCK_LIST / BLOCK_PLACEMENT_LIST / BLOCK_POS / BOOLEAN |
| 出 | Block Placements / Anchors / Instance Count / Placement Count | … |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Instance Block Placements].Anchors
[Instance Block Placements].Block Placements ──► [下游].…
```
