# Sphere By Diameter

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.sphere_from_diameter` |
| 分类 | `geometry.primitives` |
| 显示名称 | Sphere By Diameter |
| 效果 | `PURE` |

## 作用

直径两端点定球：中心为中点，半径为半距。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Point A / B | `input_start` / `input_end` | POINT |
| 出 | Sphere / Geometry | … | SPHERE / GEOMETRY |
| 出 | Center / Radius / Diameter | … | POINT / DOUBLE / DOUBLE |
| 出 | Diameter Path | `output_diameter_path` | PATH |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Sphere By Diameter].Point A
[Sphere By Diameter].Sphere ──► [下游].…
```
