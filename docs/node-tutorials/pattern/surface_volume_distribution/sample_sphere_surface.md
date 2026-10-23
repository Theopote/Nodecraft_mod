# Sample Sphere Surface

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.surface_volume_distribution.sample_sphere_surface` |
| 分类 | `pattern.surface_volume_distribution` |
| 显示名称 | Sample Sphere Surface |
| 效果 | `PURE` |

## 作用

在球面采样点与法线（散射 / 生长工作流）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Sphere / Count / Seed | SPHERE / INTEGER |
| 出 | Points / Normals / Count | POINT_LIST / VECTOR_LIST / INTEGER |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Sample Sphere Surface].Sphere
[Sample Sphere Surface].Points ──► [下游].…
```
