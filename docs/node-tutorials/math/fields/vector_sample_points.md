# Vector Field Sample Points

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.vector_sample_points` |
| 分类 | `math.fields` |
| 显示名称 | Vector Field Sample Points |
| 效果 | `PURE` |

## 作用

对每个查询点采样向量场，输出向量列表。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Field | VECTOR_FIELD |
| 入 | Points | POINT_LIST |
| 出 | Vectors | VECTOR_LIST |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Vector Field Sample Points].Field
[Vector Field Sample Points].Vectors ──► [下游].…
```
