# Point List Bounds

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.point_list_bounds` |
| 分类 | `reference.points` |
| 显示名称 | Point List Bounds |
| 效果 | `PURE` |

## 作用

点列轴对齐包围盒：Bounding Box、Min/Max/Center、Size XYZ、Count。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points | `input_points` | POINT_LIST |
| 出 | Bounding Box | `output_bounding_box` | BOUNDING_BOX |
| 出 | Min / Max / Center Point | `output_min_point` 等 | POINT |
| 出 | Size X/Y/Z | `output_size_x` 等 | DOUBLE |
| 出 | Count | `output_count` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Point List Bounds].Points
[Point List Bounds].Bounding Box ──► [下游].…
```

## 注意事项

- 需要非空 POINT_LIST。
