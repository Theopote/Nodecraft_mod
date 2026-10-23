# SDF Blend Material Mask

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_blend_material_mask` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Blend Material Mask |
| 效果 | `PURE` |

## 作用

把距离列表映射为 0..1 平滑权重（材质混合）及 Inside 分类。常接在 Sample Points 之后。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Distances | `input_distances` | DOUBLE_LIST |
| 入 | Center | `input_center` | DOUBLE（权重 0.5 处） |
| 入 | Half Width | `input_half_width` | DOUBLE（过渡半宽 > 0） |
| 出 | Weights / Inside / Count | … | DOUBLE_LIST / BOOLEAN_LIST / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[SDF Sample Points].Distances ──► [Blend Material Mask].Distances
[Blend].Weights ──► 材质权重 / 调色板
```
