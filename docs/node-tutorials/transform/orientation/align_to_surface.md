# Align Points To Surface Normals

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.orientation.align_to_surface` |
| 分类 | `transform.orientation` |
| 显示名称 | Align Points To Surface Normals |
| 效果 | `PURE` |

## 作用

每点用表面法线作局部 up，构造朝向 Frame / 轴列表 / Plane 列表。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points / Normals / Forward Hint | POINT_LIST / VECTOR_LIST / VECTOR |
| 出 | Points / X·Y·Z Axes / Planes / Frames / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Align Points To Surface Normals].Points
[Align Points To Surface Normals].Points ──► [下游].…
```
