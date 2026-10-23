# Boolean Toggle

| 字段 | 值 |
|---|---|
| 节点 ID | `input.values.boolean_toggle` |
| 分类 | `input.values` |
| 显示名称 | Boolean Toggle |
| 效果 | `PURE` |

## 作用

画布上的开关控件，输出一个 BOOLEAN，常接到 Branch.Condition、循环 Enabled、或各类开关属性。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Value | `output_value` | BOOLEAN | 当前开关值 |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| 当前值 | `false` | 输出布尔 |
| True 标签 | `ON` | 开时显示文字 |
| False 标签 | `OFF` | 关时显示文字 |

## 使用步骤

1. 拖入节点，在节点 UI 上点击切换，或在属性里改「当前值」。
2. 把 **Value** 接到需要布尔的端口（如 Branch.Condition）。

## 典型接法

```text
[Boolean Toggle].Value ──► [Branch].Condition
[Boolean Toggle].Value ──► [For Each Loop].Enabled
```

## 注意事项

- 标签只影响显示，不改变输出类型。
- 状态会随节点属性一起保存进图。
