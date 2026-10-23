# Deconstruct Hemisphere

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_hemisphere` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Hemisphere |
| 效果 | `PURE` |

## 作用

拆半球中心、轴、半径与曲/平面积、体积。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Hemisphere | `input_hemisphere` | HEMISPHERE_GEOMETRY |
| 出 | Center / Axis / Radius | … | POINT / VECTOR / DOUBLE |
| 出 | Curved / Flat / Surface Area / Volume | … | DOUBLE |
| 出 | Region / Bounding Box | … | REGION / BOUNDING_BOX |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Hemisphere].Hemisphere
[Deconstruct Hemisphere].Center ──► [下游].…
```
