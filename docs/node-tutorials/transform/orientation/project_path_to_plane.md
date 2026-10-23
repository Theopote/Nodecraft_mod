# Project Path To Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.orientation.project_path_to_plane` |
| 分类 | `transform.orientation` |
| 显示名称 | Project Path To Plane |
| 效果 | `PURE` |

## 作用

PATH 投影到平面。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Path / Plane | PATH / PLANE |
| 出 | Path / Points / Distances / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Project Path To Plane].Path
[Project Path To Plane].Path ──► [下游].…
```
