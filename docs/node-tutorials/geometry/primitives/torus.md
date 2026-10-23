# Torus By Center Axis Radii

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.torus` |
| 分类 | `geometry.primitives` |
| 显示名称 | Torus By Center Axis Radii |
| 效果 | `PURE` |

## 作用

中心 + 对称轴 + 大半径（到管心）+ 小半径（管截面）→ 圆环。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Axis | … | POINT / VECTOR |
| 入 | Major / Minor Radius | … | DOUBLE |
| 出 | Torus / Geometry | … | TORUS_GEOMETRY / GEOMETRY |
| 出 | Axis Normalized / Major / Minor Radius | … | VECTOR / DOUBLE / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Torus By Center Axis Radii].Center
[Torus By Center Axis Radii].Torus ──► [下游].…
```

## 注意事项

- 须为**环面**：Major > Minor > 0（小半径不能 ≥ 大半径）。
