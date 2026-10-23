# Weighted Spatial Palette

| 字段 | 值 |
|---|---|
| 节点 ID | `material.basic_assignment.weighted_palette` |
| 分类 | `material.basic_assignment` |
| 显示名称 | Weighted Spatial Palette |
| 效果 | `PURE` |

## 作用

空间连贯的加权材质场（value noise + Seed）。可覆盖调色板 Weights。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Palette / Weights / Seed | … |
| 出 | Block Placements / Tree / Palette Size / Total Weight | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Weighted Spatial Palette].Block Placements
[Weighted Spatial Palette].Block Placements ──► [下游].…
```
