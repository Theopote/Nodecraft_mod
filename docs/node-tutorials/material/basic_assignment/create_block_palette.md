# Create Block Palette

| 字段 | 值 |
|---|---|
| 节点 ID | `material.basic_assignment.create_block_palette` |
| 分类 | `material.basic_assignment` |
| 显示名称 | Create Block Palette |
| 效果 | `PURE` |

## 作用

从 STRING_LIST 方块 ID + 可选权重，或 Block A–D 快捷口，构造 `BLOCK_PALETTE`。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block IDs / Weights / Block A·B·C·D | STRING_LIST / DOUBLE_LIST / BLOCK_TYPE |
| 出 | Palette / Size | BLOCK_PALETTE / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Create Block Palette].Block IDs
[Create Block Palette].Palette ──► [下游].…
```
