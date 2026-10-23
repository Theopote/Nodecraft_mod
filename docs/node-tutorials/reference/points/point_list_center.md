# Point List Center

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.point_list_center` |
| 分类 | `reference.points` |
| 显示名称 | Point List Center |
| 效果 | `PURE` |

## 作用

点列算术平均中心。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points | `input_points` | POINT_LIST |
| 出 | Center Point | `output_center_point` | POINT |
| 出 | Count | `output_count` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Point List Center].Points
[Point List Center].Center Point ──► [下游].…
```

## 注意事项

- 空列表或超 `MAX_LIST_ELEMENTS` → Valid false。
