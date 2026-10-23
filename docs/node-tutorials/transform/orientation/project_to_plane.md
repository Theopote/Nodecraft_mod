# Project Point To Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.orientation.project_to_plane` |
| 分类 | `transform.orientation` |
| 显示名称 | Project Point To Plane |
| 效果 | `PURE` |

## 作用

单点投影到平面，输出投影点与（有符号）距离。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Point / Plane | POINT / PLANE |
| 出 | Projected Point / Distance / Signed Distance | POINT / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Project Point To Plane].Point
[Project Point To Plane].Projected Point ──► [下游].…
```
