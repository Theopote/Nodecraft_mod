# Convex Hull 2D On Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.convex_hull_plane` |
| 分类 | `geometry.profiles` |
| 显示名称 | Convex Hull 2D On Plane |
| 效果 | `PURE` |

## 作用

点云投影到平面后求 2D 凸包，输出闭合轮廓。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points / Plane | … | POINT_LIST / PLANE |
| 出 | Points / Profile / Boundary / Plane / Center | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Convex Hull 2D On Plane].Points
[Convex Hull 2D On Plane].Points ──► [下游].…
```

## 注意事项

- 有效凸包至少 3 顶点。
