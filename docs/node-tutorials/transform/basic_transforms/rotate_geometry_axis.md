# Rotate Geometry Around Axis

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.rotate_geometry_axis` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Rotate Geometry Around Axis |
| 效果 | `PURE` |

## 作用

绕 Center + Axis 旋转几何；Angle 为度。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Center / Axis / Angle | GEOMETRY / POINT / VECTOR / DOUBLE |
| 出 | Geometry / Effective Axis / Effective Angle | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Rotate Geometry Around Axis].Geometry
[Rotate Geometry Around Axis].Geometry ──► [下游].…
```
