# Slerp Vectors

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.slerp` |
| 分类 | `reference.vectors` |
| 显示名称 | Slerp Vectors |
| 效果 | `PURE` |

## 作用

两方向向量球面线性插值。**T 不夹紧**，允许外推。另输出 A、B 归一化后的无符号夹角（度）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | A / B / T | … | VECTOR / VECTOR / DOUBLE |
| 出 | Result | … | VECTOR |
| 出 | Angle | … | DOUBLE（度） |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Slerp Vectors].A
[Slerp Vectors].Result ──► [下游].…
```

## 注意事项

- 适合转向插值；位置插值用 Lerp。
