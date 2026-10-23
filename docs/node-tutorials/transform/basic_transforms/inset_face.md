# Inset Box Face

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.inset_face` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Inset Box Face |
| 效果 | `PURE` |

## 作用

在面内缩进/外扩参考边界（Distance>0 内缩），不改源盒体。

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
[上游].… ──► [Inset Box Face].Face
[Inset Box Face].Face ──► [下游].…
```
