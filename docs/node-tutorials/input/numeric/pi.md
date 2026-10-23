# Pi

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.pi` |
| 分类 | `input.numeric` |
| 显示名称 | Pi |
| 效果 | `PURE` |

## 作用

输出数学常量 π（`Math.PI`），类型 DOUBLE。UI 显示名可为 `Pi (π)`。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Value | `output_value` | DOUBLE | π ≈ 3.14159… |

## 使用步骤

1. 拖入节点，把 **Value** 接到圆周、弧度换算、表达式等。

## 典型接法

```text
[Pi].Value ──► [Multiplication]（× 半径² 等）
[Pi].Value ──► [表达式 / 圆周长]
```

## 注意事项

- 常量节点，无输入；无需配置。
