# Project Vector onto Vector

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.project` |
| 分类 | `reference.vectors` |
| 显示名称 | Project Vector onto Vector |
| 效果 | `PURE` |

## 作用

把 A 投影到 B：`proj = (A·B / |B|²) B`，并输出正交分量 Rejection 与系数 Scale。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | A / B | … | VECTOR |
| 出 | Projection | … | VECTOR |
| 出 | Rejection | … | VECTOR（垂直于 B） |
| 出 | Scale | … | DOUBLE |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Project Vector onto Vector].A
[Project Vector onto Vector].Projection ──► [下游].…
```

## 注意事项

- B 为零向量会失败。
