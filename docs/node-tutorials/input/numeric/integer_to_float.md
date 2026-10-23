# Integer To Float

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.integer_to_float` |
| 分类 | `input.numeric` |
| 显示名称 | Integer To Float |
| 效果 | `PURE` |

## 作用

把 `INTEGER` 显式转为 `FLOAT`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Value | `input_value` | INTEGER |
| 出 | Value | `output_value` | FLOAT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Integer To Float].Value
[Integer To Float].Value ──► [下游].…
```

## 注意事项

- 大整数转 FLOAT 可能丢精度（FLOAT 尾数限制）；需要更高精度优先转 DOUBLE。
