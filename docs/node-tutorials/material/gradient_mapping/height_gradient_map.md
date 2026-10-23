# Height Gradient Map

| 字段 | 值 |
|---|---|
| 节点 ID | `material.gradient_mapping.height_gradient_map` |
| 分类 | `material.gradient_mapping` |
| 显示名称 | Height Gradient Map |
| 效果 | `PURE` |

## 作用

按相对 Y 高度分 Bottom / Middle / Top / Peak 四带。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Bottom / Middle / Top / Peak | … |
| 出 | Block Placements | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Height Gradient Map].Block Placements
[Height Gradient Map].Block Placements ──► [下游].…
```
