# Surface Slope Map

| 字段 | 值 |
|---|---|
| 节点 ID | `material.directional_mapping.slope_map` |
| 分类 | `material.directional_mapping` |
| 显示名称 | Surface Slope Map |
| 效果 | `PURE` |

## 作用

仅柱顶体素：按四邻柱高坡度分 Flat / Slope / Steep。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Flat / Slope / Steep | … |
| 出 | Block Placements | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Surface Slope Map].Block Placements
[Surface Slope Map].Block Placements ──► [下游].…
```
