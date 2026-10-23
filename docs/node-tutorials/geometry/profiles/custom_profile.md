# Polygon By Points

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.custom_profile` |
| 分类 | `geometry.profiles` |
| 显示名称 | Polygon By Points |
| 效果 | `PURE` |

## 作用

有序点列构造共面多边形轮廓；自动求平面、闭合边界与边数。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points | `input_points` | POINT_LIST |
| 出 | Points / Profile / Boundary / Plane / Center | … | … |
| 出 | Edges | `output_edge_count` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Polygon By Points].Points
[Polygon By Points].Points ──► [下游].…
```

## 注意事项

- 点须共面且能围成简单多边形；严格 `POINT_LIST`。
