# Rotate Block Positions

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.placement.rotate_block_positions` |
| 分类 | `transform.placement` |
| 显示名称 | Rotate Block Positions |
| 效果 | `PURE` |

## 作用

BLOCK_LIST 绕点+轴旋转后贴格。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Positions / Center / Axis / Angle | … |
| 出 | Block Positions / Effective Axis·Angle / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Rotate Block Positions].Block Positions
[Rotate Block Positions].Block Positions ──► [下游].…
```
