# Wall Slab

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.wall_slab` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Wall Slab |
| 效果 | `PURE` |

## 作用

由盒体面生成实心墙板（无洞）。挖窗：接 [Window Array](./window_array.md) + [Difference](../boolean/difference.md)。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Wall Thickness | BOX_FACE / DOUBLE |
| 出 | Wall Geometry / Top·Bottom Edge / Center Line / Exterior·Interior Face | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Wall Slab].Face
[Wall Slab].Wall Geometry ──► [下游].…
```
