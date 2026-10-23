# Scalar Field Constant

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.scalar_constant` |
| 分类 | `math.fields` |
| 显示名称 | Scalar Field Constant |
| 效果 | `PURE` |

## 作用

处处返回常数的标量场。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Value | DOUBLE |
| 出 | Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Scalar Field Constant].Value
[Scalar Field Constant].Field ──► [下游].…
```
