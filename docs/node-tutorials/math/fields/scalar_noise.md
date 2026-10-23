# Scalar Field Noise

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.scalar_noise` |
| 分类 | `math.fields` |
| 显示名称 | Scalar Field Noise |
| 效果 | `PURE` |

## 作用

世界空间确定性相干噪声标量场。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Seed | INTEGER |
| 入 | Scale | DOUBLE |
| 入 | Offset X | DOUBLE |
| 入 | Offset Y | DOUBLE |
| 入 | Offset Z | DOUBLE |
| 入 | Amplitude | DOUBLE |
| 出 | Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Scalar Field Noise].Seed
[Scalar Field Noise].Field ──► [下游].…
```
