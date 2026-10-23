# Scatter On Surface Strip

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.surface_volume_distribution.scatter_surface_strip` |
| 分类 | `pattern.surface_volume_distribution` |
| 显示名称 | Scatter On Surface Strip |
| 效果 | `PURE` |

## 作用

在 SURFACE_STRIP 上按面积加权四边形采样，可选间距。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Surface Strip / Target Count / Seed / Min Distance | SURFACE_STRIP / … |
| 出 | Points / Count | POINT_LIST / INTEGER |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Scatter On Surface Strip].Surface Strip
[Scatter On Surface Strip].Points ──► [下游].…
```
