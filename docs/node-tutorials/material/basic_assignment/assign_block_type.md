# Assign Block Type

| 字段 | 值 |
|---|---|
| 节点 ID | `material.basic_assignment.assign_block_type` |
| 分类 | `material.basic_assignment` |
| 显示名称 | Assign Block Type |
| 效果 | `PURE` |

## 作用

给占用源统一指定一种方块类型。只改 blockId，保留 stateData。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinates / Blocks Tree / Block Placements / Geometry / 各原语几何 / Block Type | … |
| 出 | Block Placements / Block Placements Tree | BLOCK_PLACEMENT_LIST / DATA_TREE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[Voxelize].Blocks ──► Coordinates
[Block Type Selector].Block Type ──► Block Type
[Assign].Block Placements ──► Apply Changes
```
