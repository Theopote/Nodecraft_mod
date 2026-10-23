# Cylinder By Axis Radius

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.cylinder` |
| 分类 | `geometry.primitives` |
| 显示名称 | Cylinder By Axis Radius |
| 效果 | `PURE` |

## 作用

轴两端点 + 半径构造圆柱。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Start / End | `input_start` / `input_end` | POINT |
| 入 | Radius | `input_radius` | DOUBLE |
| 出 | Cylinder / Geometry | … | CYLINDER_GEOMETRY / GEOMETRY |
| 出 | Axis Path / Axis Vector / Height / Radius | … | PATH / VECTOR / DOUBLE / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Cylinder By Axis Radius].Start
[Cylinder By Axis Radius].Cylinder ──► [下游].…
```

## 注意事项

- 轴长与半径须 > 0。
