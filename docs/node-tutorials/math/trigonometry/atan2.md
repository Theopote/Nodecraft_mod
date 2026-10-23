# Atan2

| 字段 | 值 |
|---|---|
| 节点 ID | `math.trigonometry.atan2` |
| 分类 | `math.trigonometry` |
| 显示名称 | Atan2 |
| 效果 | `PURE` |

## 作用

atan2(Y,X) 有符号角，结果为度。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Y | DOUBLE |
| 入 | X | DOUBLE |
| 出 | Angle | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Atan2].Y
[Atan2].Angle ──► [下游].…
```
