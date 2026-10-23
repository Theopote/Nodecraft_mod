# SDF-Driven Material

| 字段 | 值 |
|---|---|
| 节点 ID | `material.gradient_mapping.sdf_material` |
| 分类 | `material.gradient_mapping` |
| 显示名称 | SDF-Driven Material |
| 效果 | `PURE` |

## 作用

用采样 SDF 距离映射调色板（Center / Half Width 控制权重带）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / SDF / Palette / Fallback / Center / Half Width | … |
| 出 | Block Placements / Distances / Weights | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [SDF-Driven Material].Block Placements
[SDF-Driven Material].Block Placements ──► [下游].…
```
