# Noise

| 字段 | 值 |
|---|---|
| 节点 ID | `math.random.noise` |
| 分类 | `math.random` |
| 显示名称 | Noise |
| 效果 | `PURE` |

## 作用

由位置与种子采样相干 3D value noise。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | X | DOUBLE |
| 入 | Y | DOUBLE |
| 入 | Z | DOUBLE |
| 入 | Seed | INTEGER |
| 出 | Noise | DOUBLE |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Noise].X
[Noise].Noise ──► [下游].…
```
