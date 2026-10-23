# Moss Growth

| 字段 | 值 |
|---|---|
| 节点 ID | `material.surface_aging.moss_growth` |
| 分类 | `material.surface_aging` |
| 显示名称 | Moss Growth |
| 效果 | `PURE` |

## 作用

**顶面暴露**体素上，噪声低于 Amount 时铺 Moss Block。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Base Block / Moss Block / Amount / Seed / Aging Origin | … |
| 出 | Block Placements / Affected Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Moss Growth].Block Placements
[Moss Growth].Block Placements ──► [下游].…
```
