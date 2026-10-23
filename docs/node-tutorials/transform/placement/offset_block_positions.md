# Offset Block Positions

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.placement.offset_block_positions` |
| 分类 | `transform.placement` |
| 显示名称 | Offset Block Positions |
| 效果 | `PURE` |

## 作用

BLOCK_LIST 整体按 VECTOR 偏移（同样 `Math.round`）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Positions / Offset Vector | BLOCK_LIST / VECTOR |
| 出 | Block Positions / Effective Offset / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Offset Block Positions].Block Positions
[Offset Block Positions].Block Positions ──► [下游].…
```
