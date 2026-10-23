# Number Input

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.float` |
| 分类 | `input.numeric` |
| 显示名称 | Number Input |
| 效果 | `PURE` |

## 作用

精确浮点输入，输出 `DOUBLE`。Min/Max **可选**（可不设界）。需要在固定区间里快速拧参数时，改用 [Number Slider](./float_slider.md)。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Value | `output_value` | DOUBLE | 当前浮点值 |

### 属性（常用）

| 属性 | 说明 |
|---|---|
| 当前值 | 输出值 |
| 最小值 / 最大值 | 可选界；可不限制 |
| 精度 | 显示小数位（0–6） |
| 拖动速度 | 鼠标拖改灵敏度 |

## 使用步骤

1. 在节点上输入或拖改数值。
2. （可选）设 Min/Max 与精度。
3. 接到半径、高度、比例等 DOUBLE 口。

## 典型接法

```text
[Number Input].Value ──► [Sphere].Radius
[Number Input].Value ──► [数学 / 表达式].A
```

## 注意事项

- 类型是 DOUBLE（图语言主浮点），不是 FLOAT；接 FLOAT 口需 [Double To Float](./double_to_float.md)。
- 非有限值会被拒绝 / 保持上次有限值（fail-safe）。
