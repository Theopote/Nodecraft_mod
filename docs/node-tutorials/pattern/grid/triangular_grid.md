# Triangular Grid

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.grid.triangular_grid` |
| 分类 | `pattern.grid` |
| 显示名称 | Triangular Grid |
| 效果 | `PURE` |

## 作用

三角格锚点，隔行偏移；Flip 标记朝向翻转。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Origin / Side Length / U Count / V Count | POINT / DOUBLE / INTEGER |
| 出 | Points / Flip / Count | POINT_LIST / BOOLEAN_LIST / INTEGER |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Triangular Grid].Origin
[Triangular Grid].Points ──► [下游].…
```
