# Grid Pattern Map

| 字段 | 值 |
|---|---|
| 节点 ID | `material.pattern_mapping.grid_pattern_map` |
| 分类 | `material.pattern_mapping` |
| 显示名称 | Grid Pattern Map |
| 效果 | `PURE` |

## 作用

相对 Pattern Origin 的 X/Z 网格：边框 Frame、填充 Fill（Y 方向挤出）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Frame / Fill / Pattern Origin | … |
| 出 | Block Placements | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Grid Pattern Map].Block Placements
[Grid Pattern Map].Block Placements ──► [下游].…
```
