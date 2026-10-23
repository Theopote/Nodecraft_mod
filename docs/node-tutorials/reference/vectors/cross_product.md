# Cross Product

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.cross_product` |
| 分类 | `reference.vectors` |
| 显示名称 | Cross Product |
| 效果 | `PURE` |

## 作用

`A × B` 及叉积模长。常用于求平面法线。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | A / B | `input_vector_a` / `input_vector_b` | VECTOR |
| 出 | Cross Product | `output_cross_product` | VECTOR |
| 出 | Magnitude | `output_magnitude` | DOUBLE |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[边1]+[边2] ──► [Cross Product] ──► [Normalize] ──► [Construct Plane].Normal
```
