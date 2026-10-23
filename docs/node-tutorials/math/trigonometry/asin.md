# Arcsine (ArcSin)

| 字段 | 值 |
|---|---|
| 节点 ID | `math.trigonometry.asin` |
| 分类 | `math.trigonometry` |
| 显示名称 | Arcsine (ArcSin) |
| 效果 | `PURE` |

## 作用

反正弦，结果为度。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Value | DOUBLE |
| 出 | Angle | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Arcsine (ArcSin)].Value
[Arcsine (ArcSin)].Angle ──► [下游].…
```
