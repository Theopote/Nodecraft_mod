# Capsule By Axis Radius

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.capsule` |
| 分类 | `geometry.primitives` |
| 显示名称 | Capsule By Axis Radius |
| 效果 | `PURE` |

## 作用

轴两端 + 半径 → 胶囊（圆柱 + 两端半球）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Start / End / Radius | … | POINT / POINT / DOUBLE |
| 出 | Geometry | `output_geometry` | GEOMETRY |
| 出 | Cylinder | `output_cylinder` | CYLINDER_GEOMETRY |
| 出 | Start / End Hemisphere | … | HEMISPHERE_GEOMETRY |
| 出 | Radius / Axis Length / Axis Path | … | DOUBLE / DOUBLE / PATH |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Capsule By Axis Radius].Start
[Capsule By Axis Radius].Geometry ──► [下游].…
```

## 注意事项

- Start≠End；半径 > 0。
