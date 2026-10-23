# Multiplication (*)

| 字段 | 值 |
|---|---|
| 节点 ID | `math.scalar_math.multiplication` |
| 分类 | `math.scalar_math` |
| 显示名称 | Multiplication (*) |
| 效果 | `PURE` |

## 作用

A 乘 B。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | A | DOUBLE |
| 入 | B | DOUBLE |
| 出 | Product | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Multiplication (*)].A
[Multiplication (*)].Product ──► [下游].…
```
