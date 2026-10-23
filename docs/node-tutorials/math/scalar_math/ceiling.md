# Ceiling

| 字段 | 值 |
|---|---|
| 节点 ID | `math.scalar_math.ceiling` |
| 分类 | `math.scalar_math` |
| 显示名称 | Ceiling |
| 效果 | `PURE` |

## 作用

向上取整。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Value | DOUBLE |
| 出 | Ceiling | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Ceiling].Value
[Ceiling].Ceiling ──► [下游].…
```
