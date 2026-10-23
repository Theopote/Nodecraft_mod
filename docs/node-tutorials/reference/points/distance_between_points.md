# Distance Between Points

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.distance_between_points` |
| 分类 | `reference.points` |
| 显示名称 | Distance Between Points |
| 效果 | `PURE` |

## 作用

两点欧氏距离。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Point A / B | `input_point_a` / `input_point_b` | POINT |
| 出 | Distance | `output_distance` | DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Distance Between Points].Point A
[Distance Between Points].Distance ──► [下游].…
```

## 注意事项

- 失败时 Distance 可能为 0，务必看 Valid。
