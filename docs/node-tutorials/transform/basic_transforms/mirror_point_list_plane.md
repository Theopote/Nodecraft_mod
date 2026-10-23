# Mirror Point List About Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.mirror_point_list_plane` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Mirror Point List About Plane |
| 效果 | `PURE` |

## 作用

POINT_LIST 中每个点关于平面镜像。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points / Plane | POINT_LIST / PLANE |
| 出 | Points / Count | POINT_LIST / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Mirror Point List About Plane].Points
[Mirror Point List About Plane].Points ──► [下游].…
```
