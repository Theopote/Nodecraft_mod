# Hex Grid

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.grid.hex_grid` |
| 分类 | `pattern.grid` |
| 显示名称 | Hex Grid |
| 效果 | `PURE` |

## 作用

XZ 平面六角格锚点（Q/R 计数，Hex Radius）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Origin / Hex Radius / Q Count / R Count | POINT / DOUBLE / INTEGER |
| 出 | Points / Count | POINT_LIST / INTEGER |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Hex Grid].Origin
[Hex Grid].Points ──► [下游].…
```
