# Staggered Grid

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.grid.staggered_grid` |
| 分类 | `pattern.grid` |
| 显示名称 | Staggered Grid |
| 效果 | `PURE` |

## 作用

交错格锚点：奇偶行用 Stagger Offset 错位。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Origin / Step·Row Direction / Step·Row Distance / Stagger Offset / Counts | … |
| 出 | Points / Count | POINT_LIST / INTEGER |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Staggered Grid].Origin
[Staggered Grid].Points ──► [下游].…
```
