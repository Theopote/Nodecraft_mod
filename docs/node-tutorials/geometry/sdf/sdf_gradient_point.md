# SDF Gradient At Point

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_gradient_point` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Gradient At Point |
| 效果 | `PURE` |

## 作用

有限差分求梯度并归一化，得到近似表面法线方向；同时输出该点距离。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | SDF / Point / Step | … | SDF / POINT / DOUBLE（步长 > 0） |
| 出 | Gradient | `output_gradient` | VECTOR |
| 出 | Distance | `output_distance` | DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Gradient At Point].SDF
[SDF Gradient At Point].Gradient ──► [下游].…
```
