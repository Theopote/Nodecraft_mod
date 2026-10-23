# Not Equals (!=)

| 字段 | 值 |
|---|---|
| 节点 ID | `math.compare.not_equals` |
| 分类 | `math.compare` |
| 显示名称 | Not Equals (!=) |
| 效果 | `PURE` |

## 作用

A 不等于 B 时返回 true。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | A | ANY |
| 入 | B | ANY |
| 出 | Result | BOOLEAN |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Not Equals (!=)].A
[Not Equals (!=)].Result ──► [下游].…
```
