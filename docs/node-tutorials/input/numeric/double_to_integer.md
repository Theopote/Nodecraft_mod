# Double To Integer

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.double_to_integer` |
| 分类 | `input.numeric` |
| 显示名称 | Double To Integer |
| 效果 | `PURE` |

## 作用

仅当 DOUBLE 为**精确整数**且落在 INT32 范围内时，转为 `INTEGER`。不是四舍五入 / 截断——`3.1` 会失败。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Value | `input_value` | DOUBLE |
| 出 | Value | `output_value` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 确认上游值本就是整数（如 `4.0`）。
2. 接到本节点；Valid false 时检查是否有小数部分或越界。

## 典型接法

```text
[上游].… ──► [Double To Integer].In
[Double To Integer].Out ──► [下游].…
```

## 注意事项

- 需要「取整」请用数学节点（Floor/Ceil/Round），不要指望本节点做舍入。
