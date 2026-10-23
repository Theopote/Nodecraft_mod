# Project Profile To Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.orientation.project_profile_to_plane` |
| 分类 | `transform.orientation` |
| 显示名称 | Project Profile To Plane |
| 效果 | `PURE` |

## 作用

多边形轮廓边界投影到目标平面。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Profile / Plane | POLYGON_PROFILE / PLANE |
| 出 | Profile / Boundary / Points / Distances / Edge Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Project Profile To Plane].Profile
[Project Profile To Plane].Profile ──► [下游].…
```
