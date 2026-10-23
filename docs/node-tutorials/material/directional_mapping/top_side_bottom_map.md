# Column Layer Map

| 字段 | 值 |
|---|---|
| 节点 ID | `material.directional_mapping.top_side_bottom_map` |
| 分类 | `material.directional_mapping` |
| 显示名称 | Column Layer Map |
| 效果 | `PURE` |

## 作用

按 X/Z 柱分层：最高→Top、最低→Bottom、中间→Side。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Top / Side / Bottom | … |
| 出 | Block Placements | BLOCK_PLACEMENT_LIST |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Column Layer Map].Block Placements
[Column Layer Map].Block Placements ──► [下游].…
```
