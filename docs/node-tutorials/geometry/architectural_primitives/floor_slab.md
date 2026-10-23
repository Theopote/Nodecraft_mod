# Floor Slab

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.floor_slab` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Floor Slab |
| 效果 | `PURE` |

## 作用

由盒体面足迹生成楼板实体，并输出 Top / Bottom Face（可挂 [Beam Grid](./beam_grid.md)）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Thickness | BOX_FACE / DOUBLE |
| 出 | Geometry / Top Face / Bottom Face | GEOMETRY / BOX_FACE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Floor Slab].Face
[Floor Slab].Geometry ──► [下游].…
```
