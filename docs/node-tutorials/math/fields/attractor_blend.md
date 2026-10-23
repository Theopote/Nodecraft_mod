# Blend Vector Fields

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.attractor_blend` |
| 分类 | `math.fields` |
| 显示名称 | Blend Vector Fields |
| 效果 | `PURE` |

## 作用

最多四个向量场按权重混合。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Field A | VECTOR_FIELD |
| 入 | Field B | VECTOR_FIELD |
| 入 | Field C | VECTOR_FIELD |
| 入 | Field D | VECTOR_FIELD |
| 入 | Weight A | DOUBLE |
| 入 | Weight B | DOUBLE |
| 入 | Weight C | DOUBLE |
| 入 | Weight D | DOUBLE |
| 出 | Field | VECTOR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Blend Vector Fields].Field A
[Blend Vector Fields].Field ──► [下游].…
```
