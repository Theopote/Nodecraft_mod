# Translate Point

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.translate_point` |
| 分类 | `reference.points` |
| 显示名称 | Translate Point |
| 效果 | `PURE` |

## 作用

`Point + Offset(VECTOR) → Point`，位移向量按原样相加（不归一化）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Point | `input_point` | POINT |
| 入 | Offset | `input_offset` | VECTOR |
| 出 | Point | `output_point` | POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[Construct Point].Point + [Construct Vector].Vector ──► [Translate Point]
```

## 注意事项

- 要「沿方向移动固定距离」用 [Move Point Along Direction](./point_along_vector.md)（会归一化方向）。
