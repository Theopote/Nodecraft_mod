# SDF Sphere

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_sphere` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Sphere |
| 效果 | `PURE` |

## 作用

中心 + 半径构造球 SDF。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Radius | `input_center` / `input_radius` | POINT / DOUBLE |
| 出 | SDF | `output_sdf` | SDF |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Sphere].Center
[SDF Sphere].SDF ──► [下游].…
```

## 注意事项

- Center 须有限；Radius > 0（未接时用面板默认）。
