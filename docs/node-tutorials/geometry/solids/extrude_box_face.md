# Extrude Box Face

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.extrude_box_face` |
| 分类 | `geometry.solids` |
| 显示名称 | Extrude Box Face |
| 效果 | `PURE` |

## 作用

沿盒体面法线挤出一段新盒，输出源+段的复合 Geometry。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Box Geometry | … | BOX_GEOMETRY |
| 入 | Face / Face Index | … | BOX_FACE（可选）/ INTEGER 0–5 |
| 入 | Distance | … | DOUBLE（有符号） |
| 出 | Geometry / Extruded Box / Found / Resolved Face Index | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Extrude Box Face].Box Geometry
[Extrude Box Face].Geometry ──► [下游].…
```
