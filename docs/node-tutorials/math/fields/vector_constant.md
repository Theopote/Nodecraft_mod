# Vector Field Constant

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.vector_constant` |
| 分类 | `math.fields` |
| 显示名称 | Vector Field Constant |
| 效果 | `PURE` |

## 作用

处处返回常向量的向量场。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | X | DOUBLE |
| 入 | Y | DOUBLE |
| 入 | Z | DOUBLE |
| 出 | Field | VECTOR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Vector Field Constant].X
[Vector Field Constant].Field ──► [下游].…
```
