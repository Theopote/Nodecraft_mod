# Scale Block Positions

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.placement.scale_block_positions` |
| 分类 | `transform.placement` |
| 显示名称 | Scale Block Positions |
| 效果 | `PURE` |

## 作用

相对 Center 缩放方块坐标。Scale Factor 或 Scale Vector 分量须 > 0；反射用 Mirror。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Positions / Center / Scale Factor / Scale Vector | … |
| 出 | Block Positions / Effective Scale / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Scale Block Positions].Block Positions
[Scale Block Positions].Block Positions ──► [下游].…
```
