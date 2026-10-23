# Move Point Along Direction

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.point_along_vector` |
| 分类 | `reference.points` |
| 显示名称 | Move Point Along Direction |
| 效果 | `PURE` |

## 作用

从起点沿 **Direction**（**始终先归一化**）移动 **Distance**（可为负）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Point | `input_point` | POINT |
| 入 | Direction | `input_vector` | VECTOR |
| 入 | Distance | `input_distance` | DOUBLE |
| 出 | Point | `output_point` | POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Move Point Along Direction].Point
[Move Point Along Direction].Point ──► [下游].…
```

## 注意事项

- Direction 长度被忽略，只取方向；零向量失败。
- 与 Translate 的区别：Translate 用完整位移向量。
