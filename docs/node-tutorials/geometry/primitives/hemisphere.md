# Hemisphere By Center Axis Radius

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.hemisphere` |
| 分类 | `geometry.primitives` |
| 显示名称 | Hemisphere By Center Axis Radius |
| 效果 | `PURE` |

## 作用

实心半球：球心在平底圆上，沿 Axis 正向为穹顶（`dot(p−center, axis) ≥ 0`）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Axis / Radius | … | POINT / VECTOR / DOUBLE |
| 出 | Hemisphere / Geometry | … | HEMISPHERE_GEOMETRY / GEOMETRY |
| 出 | Axis Normalized / Radius | … | VECTOR / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Hemisphere By Center Axis Radius].Center
[Hemisphere By Center Axis Radius].Hemisphere ──► [下游].…
```

## 注意事项

- Axis 须可归一化；半径 > 0。
