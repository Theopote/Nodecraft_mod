# Extrude Point List

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.extrude_from_points` |
| 分类 | `geometry.solids` |
| 显示名称 | Extrude Point List |
| 效果 | `PURE` |

## 作用

有序点列沿方向挤出；输出源/顶路径、侧线段与 Surface Strip（非棱柱实体）。面板 Close Path。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points / Direction | … | POINT_LIST / VECTOR |
| 出 | Source / Extruded Points · Paths | … | POINT_LIST / PATH |
| 出 | Side Segments / Surface Strip / Count | … | LINE_LIST / SURFACE_STRIP / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Extrude Point List].Points
[Extrude Point List].Source ──► [下游].…
```
