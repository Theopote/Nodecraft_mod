# Shrinkwrap Points On Surface Strip

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.shrinkwrap_points_surface_strip` |
| 分类 | `geometry.solids` |
| 显示名称 | Shrinkwrap Points On Surface Strip |
| 效果 | `PURE` |

## 作用

查询点投影到条带三角网格最近点。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points / Surface Strip | … | POINT_LIST / SURFACE_STRIP |
| 出 | Projected Points / Distances | … | POINT_LIST / DOUBLE_LIST |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Shrinkwrap Points On Surface Strip].Points
[Shrinkwrap Points On Surface Strip].Projected Points ──► [下游].…
```
