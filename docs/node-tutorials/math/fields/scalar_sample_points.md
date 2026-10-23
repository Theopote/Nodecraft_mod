# Scalar Field Sample Points

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.scalar_sample_points` |
| 分类 | `math.fields` |
| 显示名称 | Scalar Field Sample Points |
| 效果 | `PURE` |

## 作用

对每个查询点采样标量场，输出数值列表。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Field | SCALAR_FIELD |
| 入 | Points | POINT_LIST |
| 出 | Values | DOUBLE_LIST |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Scalar Field Sample Points].Field
[Scalar Field Sample Points].Values ──► [下游].…
```
