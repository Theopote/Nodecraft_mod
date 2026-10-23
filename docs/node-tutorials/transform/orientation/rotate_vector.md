# Rotate Vector

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.orientation.rotate_vector` |
| 分类 | `transform.orientation` |
| 显示名称 | Rotate Vector |
| 效果 | `PURE` |

## 作用

向量绕 Axis 旋转 Angle（度）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Vector / Axis / Angle | VECTOR / VECTOR / DOUBLE |
| 出 | Rotated Vector | VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Rotate Vector].Vector
[Rotate Vector].Rotated Vector ──► [下游].…
```
