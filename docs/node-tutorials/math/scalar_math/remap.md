# Remap

| 字段 | 值 |
|---|---|
| 节点 ID | `math.scalar_math.remap` |
| 分类 | `math.scalar_math` |
| 显示名称 | Remap |
| 效果 | `PURE` |

## 作用

从源域线性映射到目标域。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Value | DOUBLE |
| 入 | Source | NUMERIC_RANGE |
| 入 | Target | NUMERIC_RANGE |
| 入 | Clamp | BOOLEAN |
| 出 | Result | DOUBLE |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Remap].Value
[Remap].Result ──► [下游].…
```
