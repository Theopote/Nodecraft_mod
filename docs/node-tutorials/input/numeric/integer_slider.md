# Integer Slider

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.integer_slider` |
| 分类 | `input.numeric` |
| 显示名称 | Integer Slider |
| 效果 | `PURE` |

## 作用

在有界整数区间上用滑动条调参，输出 `INTEGER`。默认范围 0–100，当前值 50。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Value | `output_value` | INTEGER | 选中的整数 |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| 当前值 | `50` | 输出 |
| 最小值 / 最大值 | `0` / `100` | 滑条范围 |
| 步长 | `1` | ± 按钮步进 |
| 显示数值输入 | `true` | 滑条上方输入框 |
| 紧凑模式 | `false` | 减少节点高度 |

## 使用步骤

1. 设好 Min/Max，拖动滑条或输入框改值。
2. 把 **Value** 接到需要有界整数的下游。

## 典型接法

```text
[Integer Slider].Value ──► [Repeat / 层数 / 分段数]
```

## 注意事项

- 适合「调参探索」；要敲任意大整数用 [Integer Input](./integer.md)。
