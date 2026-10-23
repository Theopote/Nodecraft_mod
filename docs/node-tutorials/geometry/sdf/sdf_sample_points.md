# SDF Sample Points

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_sample_points` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Sample Points |
| 效果 | `PURE` |

## 作用

对点列逐点采样，输出对齐的距离表与 Inside 表。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | SDF / Points | … | SDF / POINT_LIST |
| 出 | Distances | `output_distances` | DOUBLE_LIST |
| 出 | Inside | `output_inside` | BOOLEAN_LIST |
| 出 | Count | `output_count` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Sample Points].SDF
[SDF Sample Points].Distances ──► [下游].…
```

## 注意事项

- 任一点非法则整体失败（不静默丢弃）；列表有长度上限。
