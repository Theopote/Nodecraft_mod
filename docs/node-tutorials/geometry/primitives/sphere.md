# Sphere By Center Radius

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.sphere` |
| 分类 | `geometry.primitives` |
| 显示名称 | Sphere By Center Radius |
| 效果 | `PURE` |

## 作用

中心 + 半径构造球。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center | `input_center` | POINT（可选） |
| 入 | Radius | `input_radius` | DOUBLE（可选，有默认） |
| 出 | Sphere | `output_sphere` | SPHERE |
| 出 | Geometry | `output_geometry` | GEOMETRY |
| 出 | Center / Radius / Diameter | … | POINT / DOUBLE / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Sphere By Center Radius].Center
[Sphere By Center Radius].Sphere ──► [下游].…
```

## 注意事项

- Radius 须有限且 > 0。
