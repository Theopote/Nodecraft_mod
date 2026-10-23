# Smoothstep

| 字段 | 值 |
|---|---|
| 节点 ID | `math.scalar_math.smoothstep` |
| 分类 | `math.scalar_math` |
| 显示名称 | Smoothstep |
| 效果 | `PURE` |

## 作用

edge0–edge1 上 Hermite 光滑插值 3t²−2t³。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Value | DOUBLE |
| 入 | Edge 0 | DOUBLE |
| 入 | Edge 1 | DOUBLE |
| 出 | Result | DOUBLE |
| 出 | T | DOUBLE |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Smoothstep].Value
[Smoothstep].Result ──► [下游].…
```
