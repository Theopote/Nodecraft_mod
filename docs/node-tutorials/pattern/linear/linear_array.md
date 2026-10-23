# Linear Array

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.linear.linear_array` |
| 分类 | `pattern.linear` |
| 显示名称 | Linear Array |
| 效果 | `PURE` |

## 作用

沿方向向量按 Distance 重复复制几何。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Direction / Distance / Count | GEOMETRY / VECTOR / DOUBLE / INTEGER |
| 出 | Geometry / Geometry Tree / Count | … |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Linear Array].Geometry
[Linear Array].Geometry ──► [下游].…
```
