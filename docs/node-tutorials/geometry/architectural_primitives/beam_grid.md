# Beam Grid

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.beam_grid` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Beam Grid |
| 效果 | `PURE` |

## 作用

在参考面上生成梁格。挂在楼板下时接 Floor Slab 的 **Bottom Face**；Beam Drop 沿面法线下沉。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Columns / Rows / Beam Width·Depth / Beam Drop / Margin | … |
| 出 | Geometry / Frames / Centers / Center Lines / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Beam Grid].Face
[Beam Grid].Geometry ──► [下游].…
```
