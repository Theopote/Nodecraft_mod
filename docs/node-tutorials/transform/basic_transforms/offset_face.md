# Offset Box Face

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.offset_face` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Offset Box Face |
| 效果 | `PURE` |

## 作用

沿面法线平移参考 **BOX_FACE**（不改源盒体几何）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Distance | BOX_FACE / DOUBLE |
| 出 | Face | BOX_FACE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Offset Box Face].Face
[Offset Box Face].Face ──► [下游].…
```
