# Angle Between Vectors

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.angle_between` |
| 分类 | `reference.vectors` |
| 显示名称 | Angle Between Vectors |
| 效果 | `PURE` |

## 作用

两向量夹角（**度**）。可选 Reference 给出有符号角（右手定则绕 Reference）；未接 Reference 时 Signed Angle 为 NaN。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | A / B | … | VECTOR |
| 入 | Reference | … | VECTOR（可选） |
| 出 | Angle | … | DOUBLE（无符号度） |
| 出 | Signed Angle | … | DOUBLE |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Angle Between Vectors].A
[Angle Between Vectors].Angle ──► [下游].…
```

## 注意事项

- A、B 须非零。Reference 不必与 A/B 垂直，仅用于符号。
