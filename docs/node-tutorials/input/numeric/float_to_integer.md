# Float To Integer

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.float_to_integer` |
| 分类 | `input.numeric` |
| 显示名称 | Float To Integer |
| 效果 | `PURE` |

## 作用

仅当 FLOAT 为有限且表示**精确整数**（并在 INT32 内）时转为 `INTEGER`。不做四舍五入。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Value | `input_value` | FLOAT |
| 出 | Value | `output_value` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Float To Integer].Value
[Float To Integer].Value ──► [下游].…
```

## 注意事项

- 与 Double To Integer 同策略：非整数失败；要取整用数学节点。
