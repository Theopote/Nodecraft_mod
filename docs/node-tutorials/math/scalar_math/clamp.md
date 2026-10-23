# Clamp

| 字段 | 值 |
|---|---|
| 节点 ID | `math.scalar_math.clamp` |
| 分类 | `math.scalar_math` |
| 显示名称 | Clamp |
| 效果 | `PURE` |

## 作用

钳到域的上下界（忽略方向）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Value | DOUBLE |
| 入 | Domain | NUMERIC_RANGE |
| 出 | Result | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Clamp].Value
[Clamp].Result ──► [下游].…
```
