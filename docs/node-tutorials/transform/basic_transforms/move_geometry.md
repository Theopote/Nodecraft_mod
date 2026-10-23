# Move Geometry

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.move_geometry` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Move Geometry |
| 效果 | `PURE` |

## 作用

解析几何按 Translation 向量平移。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Translation | GEOMETRY / VECTOR |
| 出 | Geometry / Effective Translation | GEOMETRY / VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Move Geometry].Geometry
[Move Geometry].Geometry ──► [下游].…
```
