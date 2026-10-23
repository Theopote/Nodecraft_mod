# Distance-Based Material

| 字段 | 值 |
|---|---|
| 节点 ID | `material.gradient_mapping.distance_material` |
| 分类 | `material.gradient_mapping` |
| 显示名称 | Distance-Based Material |
| 效果 | `PURE` |

## 作用

按到**恰好一个**参考（Point / Plane / Curve / Polyline / Line）的距离，在 Min–Max 内映射调色板。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Palette / Fallback / Min·Max Distance / Reference* | … |
| 出 | Block Placements / Distances | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Distance-Based Material].Block Placements
[Distance-Based Material].Block Placements ──► [下游].…
```
