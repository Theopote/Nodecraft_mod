# Project Points To Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.orientation.project_points_to_plane` |
| 分类 | `transform.orientation` |
| 显示名称 | Project Points To Plane |
| 效果 | `PURE` |

## 作用

点列投影到目标平面。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points / Plane | POINT_LIST / PLANE |
| 出 | Points / Distances / Signed Distances / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Project Points To Plane].Points
[Project Points To Plane].Points ──► [下游].…
```
