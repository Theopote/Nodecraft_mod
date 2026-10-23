# Number Slider

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.float_slider` |
| 分类 | `input.numeric` |
| 显示名称 | Number Slider |
| 效果 | `PURE` |

## 作用

有界浮点参数探索：滑动条 + 可选精确输入，输出 `DOUBLE`。**Min/Max 必须有效**（非法跨度会回退到 0–100）。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Value | `output_value` | DOUBLE | 当前值（夹在 Min–Max 内） |

### 属性

| 属性 | 说明 |
|---|---|
| 当前值 | 输出 |
| 最小值 / 最大值 | 必须构成有限可用跨度 |
| 小数位 | 显示格式 |
| 显示数值输入 | 是否显示精确输入行 |

## 使用步骤

1. 先设 Min/Max（例如半径 0.5–32）。
2. 拖滑条或输入框调参。
3. 接到几何尺寸、混合权重等。

## 典型接法

```text
[Number Slider].Value ──► [Cylinder].Radius
[Number Slider].Value ──► [Gradient Ramp].T（若已归一化）
```

## 注意事项

- 与 Number Input 的区别：Slider **强制有界**；Input 的 Min/Max 可选。
- 值始终夹紧到 [Min, Max]。
