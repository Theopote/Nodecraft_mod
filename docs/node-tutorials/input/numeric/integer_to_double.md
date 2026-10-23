# Integer To Double

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.integer_to_double` |
| 分类 | `input.numeric` |
| 显示名称 | Integer To Double |
| 效果 | `PURE` |

## 作用

把 `INTEGER` **显式**转为 `DOUBLE`。图语言不隐式拓宽类型时用此节点。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Value | `input_value` | INTEGER |
| 出 | Value | `output_value` | DOUBLE |
| 出 | Valid | `output_valid` | BOOLEAN |
| 出 | Error | `output_error` | STRING |

## 使用步骤

1. 把整数线接到 **Value** 入。
2. 用 **Value** 出接到 DOUBLE 口；失败看 Valid / Error。

## 典型接法

```text
[上游].… ──► [Integer To Double].In
[Integer To Double].Out ──► [下游].…
```

## 注意事项

- 输入必须是 INTEGER；否则 Valid false。
