# Scale Geometry Around Point

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.scale_geometry_point` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Scale Geometry Around Point |
| 效果 | `PURE` |

## 作用

绕 Center **均匀**缩放；Scale 必须 > 0。反射用 [Mirror](./mirror_geometry_plane.md)。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Center / Scale | GEOMETRY / POINT / DOUBLE |
| 出 | Geometry / Effective Scale | GEOMETRY / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Scale Geometry Around Point].Geometry
[Scale Geometry Around Point].Geometry ──► [下游].…
```
