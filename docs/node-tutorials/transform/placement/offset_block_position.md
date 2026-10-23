# Offset Block Position

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.placement.offset_block_position` |
| 分类 | `transform.placement` |
| 显示名称 | Offset Block Position |
| 效果 | `PURE` |

## 作用

单个 BLOCK_POS 偏移：整数 X/Y/Z，或 VECTOR（`Math.round`，半数向 +∞）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Position / Offset Vector / Offset X·Y·Z | BLOCK_POS / VECTOR / INTEGER |
| 出 | Block Position / Effective Offset | BLOCK_POS / VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Offset Block Position].Block Position
[Offset Block Position].Block Position ──► [下游].…
```
