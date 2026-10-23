# Block Palette

| 字段 | 值 |
|---|---|
| 节点 ID | `material.basic_assignment.block_palette` |
| 分类 | `material.basic_assignment` |
| 显示名称 | Block Palette |
| 效果 | `PURE` |

## 作用

按调色板**循环**赋材质：扁平按条目，树按分支。Start Index 为起始下标。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Coordinates / Geometry / Box·Cylinder·Sphere·Torus （占用源，按节点可选其一） / Palette / Start Index | … |
| 出 | Block Placements / Tree / Palette Size | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

> 多数映射节点只改 **blockId**，保留已有 **stateData**。

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Block Palette].Block Placements
[Block Palette].Block Placements ──► [下游].…
```
