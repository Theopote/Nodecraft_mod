# Molding Profile

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.molding_profile` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Molding Profile |
| 效果 | `PURE` |

## 作用

装饰线脚截面：`flat` / `step` / `cove` / `ogee` / `bevel`。输出 POLYGON_PROFILE，可再 Extrude / Sweep。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Plane / Center（可选定位）/ Profile Type / Width·Height·Depth / Segments | … |
| 出 | Profile / Points / Boundary / Plane | POLYGON_PROFILE / … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Molding Profile].Face
[Molding Profile].Profile ──► [下游].…
```
