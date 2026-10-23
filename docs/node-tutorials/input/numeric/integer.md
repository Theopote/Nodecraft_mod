# Integer Input

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.integer` |
| 分类 | `input.numeric` |
| 显示名称 | Integer Input |
| 效果 | `PURE` |

## 作用

手动输入整数值（可设步长与可选范围），输出 `INTEGER`。适合计数、层数、重复次数等。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Value | `output_value` | INTEGER | 当前整数 |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| 当前值 | `0` | 输出值 |
| 最小值 / 最大值 | `INT_MIN` / `INT_MAX` | 允许范围 |
| 步长 | `1` | 增减按钮步进 |

## 使用步骤

1. 拖入节点，在输入框改数值或用 ± 按钮。
2. 需要限制范围时设最小值 / 最大值。
3. 把 **Value** 接到整数口（如 Sequence.Step Count、列表长度等）。

## 典型接法

```text
[Integer Input].Value ──► [Sequence].Step Count
[Integer Input].Value ──► [Linear Array].Count
```

## 注意事项

- 输出严格为 INTEGER；接到 DOUBLE 口通常需显式转换节点。
