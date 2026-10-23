# Mirror Geometry About Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.mirror_geometry_plane` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Mirror Geometry About Plane |
| 效果 | `PURE` |

## 作用

关于平面镜像解析几何（复合与布尔节点递归镜像）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Plane | GEOMETRY / PLANE |
| 出 | Geometry | GEOMETRY |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Mirror Geometry About Plane].Geometry
[Mirror Geometry About Plane].Geometry ──► [下游].…
```
