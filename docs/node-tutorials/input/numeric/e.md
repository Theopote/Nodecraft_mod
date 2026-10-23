# E

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.e` |
| 分类 | `input.numeric` |
| 显示名称 | E |
| 效果 | `PURE` |

## 作用

输出自然常数 e（`Math.E` ≈ 2.71828…），类型 DOUBLE。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Value | `output_value` | DOUBLE | e |

## 使用步骤

1. 拖入节点，接到指数 / 衰减 / 表达式。

## 典型接法

```text
[E].Value ──► [Power / Expression] 的底数或因子
```

## 注意事项

- 与 Pi 相同：无输入的常量源。
