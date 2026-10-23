# SDF Box

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_box` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Box |
| 效果 | `PURE` |

## 作用

轴对齐盒 SDF：中心 + 半尺寸向量。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center | `input_center` | POINT |
| 入 | Half Extents | `input_half_extents` | VECTOR（各分量 > 0） |
| 出 | SDF | `output_sdf` | SDF |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Box].Center
[SDF Box].SDF ──► [下游].…
```
