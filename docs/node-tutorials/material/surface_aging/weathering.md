# Weathering

| 字段 | 值 |
|---|---|
| 节点 ID | `material.surface_aging.weathering` |
| 分类 | `material.surface_aging` |
| 显示名称 | Weathering |
| 效果 | `PURE` |

## 作用

占用暴露体素：连贯 value-noise∈[0,1] 低于 Amount 时改为 Aged Block。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Base Block / Aged Block / Amount / Seed / Aging Origin | … |
| 出 | Block Placements / Affected Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Weathering].Block Placements
[Weathering].Block Placements ──► [下游].…
```
