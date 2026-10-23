# Color Picker

| 字段 | 值 |
|---|---|
| 节点 ID | `input.values.color_picker` |
| 分类 | `input.values` |
| 显示名称 | Color Picker |
| 效果 | `PURE` |

## 作用

可视化选色，输出 COLOR 以及 R/G/B/A（0–1）通道。用于预览着色、标签颜色、材质相关参数等。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Color | `output_color` | COLOR | 完整颜色值 |
| Red / Green / Blue / Alpha | `output_red` 等 | DOUBLE | 各通道 0–1 |

## 使用步骤

1. 拖入节点，在色板 / RGB 控件里调色。
2. 优先接 **Color**；若下游只要某一通道，用 R/G/B/A。

## 典型接法

```text
[Color Picker].Color ──► [Preview Labels / 着色相关节点].Color
[Color Picker].Red   ──► （可选）标量场 / 表达式
```

## 注意事项

- 通道是归一化 0–1，不是 0–255。
- 是否包含 Alpha 编辑取决于节点 UI 设置；关闭 Alpha 编辑时 Alpha 通道仍可能保留原值。
