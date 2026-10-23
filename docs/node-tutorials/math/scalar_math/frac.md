# Fraction (Frac)

| 字段 | 值 |
|---|---|
| 节点 ID | `math.scalar_math.frac` |
| 分类 | `math.scalar_math` |
| 显示名称 | Fraction (Frac) |
| 效果 | `PURE` |

## 作用

小数部分 x−floor(x)，落在 [0,1)；frac(−1.2)=0.8。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Value | DOUBLE |
| 出 | Frac | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Fraction (Frac)].Value
[Fraction (Frac)].Frac ──► [下游].…
```
