# Place Geometry On Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.placement.place_geometry_on_plane` |
| 分类 | `transform.placement` |
| 显示名称 | Place Geometry On Plane |
| 效果 | `PURE` |

## 作用

放到平面上：先建 Frame（Z=法线，X 由 Hint），再把 Pivot 映射到平面原点。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Pivot / Plane / X Hint | … |
| 出 | Geometry / Frame | GEOMETRY / FRAME |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Place Geometry On Plane].Geometry
[Place Geometry On Plane].Geometry ──► [下游].…
```
