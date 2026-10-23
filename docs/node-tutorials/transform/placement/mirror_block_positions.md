# Mirror Block Positions

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.placement.mirror_block_positions` |
| 分类 | `transform.placement` |
| 显示名称 | Mirror Block Positions |
| 效果 | `PURE` |

## 作用

方块列表关于平面镜像并贴格。可用 Plane，或 Point+Normal。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Positions / Plane / Point / Normal | BLOCK_LIST / PLANE / POINT / VECTOR |
| 出 | Block Positions / Input Count / Output Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Mirror Block Positions].Block Positions
[Mirror Block Positions].Block Positions ──► [下游].…
```
