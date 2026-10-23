# Value Monitor

| 字段 | 值 |
|---|---|
| 节点 ID | `output.debug.value_monitor` |
| 分类 | `output.debug` |
| 显示名称 | Value Monitor |
| 效果 | `PURE` |

## 作用

恒等透传：面板实时显示已连接值与类型。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Input | `input_value` | ANY |
| 出 | Output | `output_value` | ANY |

## 使用步骤

1. 按端点表连接输入。
2. 查看输出并接到下游。

## 典型接法

```text
[上游].… ──► [Value Monitor].Input
[Value Monitor].Output ──► [下游].…
```

## 注意事项

- 不改变数据，只用于调试观察。
