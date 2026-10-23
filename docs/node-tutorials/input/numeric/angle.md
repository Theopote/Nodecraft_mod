# Angle Slider

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.angle` |
| 分类 | `input.numeric` |
| 显示名称 | Angle Slider |
| 效果 | `PURE` |

## 作用

用滑条调节角度，输出 **度**（DOUBLE）。默认 0–360。需要弧度时接 Degrees To Radians（`math.trigonometry`）。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Angle | `output_angle` | DOUBLE | 当前角度（度） |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| 当前角度 | `0` | 输出（度） |
| 最小 / 最大角度 | `0` / `360` | 滑条范围 |
| 显示范围输入 | `true` | 是否显示 Min/Max 输入 |

## 使用步骤

1. 设范围（例如 -90～90 或 0～360）。
2. 拖滑条调角度。
3. 接到旋转、弧、朝向等以**度为单位**的口。

## 典型接法

```text
[Angle Slider].Angle ──► [Rotate / Arc].Angle
[Angle Slider].Angle ──► [Degrees To Radians].In ──► 需要弧度的口
```

## 注意事项

- 图语言约定角度为度；不要假定下游是弧度。
- 非有限角度会被拒绝，保持有限值。
