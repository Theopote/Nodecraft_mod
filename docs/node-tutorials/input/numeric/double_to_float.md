# Double To Float

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.double_to_float` |
| 分类 | `input.numeric` |
| 显示名称 | Double To Float |
| 效果 | `PURE` |

## 作用

把有限 `DOUBLE` 转为 `FLOAT`（在可表示时）。精度可能损失。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Value | `input_value` | DOUBLE |
| 出 | Value | `output_value` | FLOAT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Double To Float].Value
[Double To Float].Value ──► [下游].…
```

## 注意事项

- 不可表示或非有限 → Valid false。多数图主路径用 DOUBLE，仅在下游明确要 FLOAT 时转换。
