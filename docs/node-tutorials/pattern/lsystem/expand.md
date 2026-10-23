# L-System Expand

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.lsystem.expand` |
| 分类 | `pattern.lsystem` |
| 显示名称 | L-System Expand |
| 效果 | `PURE` |

## 作用

从 Axiom 按规则迭代展开（最长符号匹配；权重相对）。Seed 影响随机选择。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Axiom / Rules / Iterations / Seed | STRING / L_SYSTEM_RULE_LIST / INTEGER |
| 出 | String / Iterations Applied / Hit Limit | STRING / INTEGER / BOOLEAN |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [L-System Expand].Axiom
[L-System Expand].String ──► [下游].…
```
