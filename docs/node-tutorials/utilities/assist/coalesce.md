# Coalesce

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.assist.coalesce` |
| 分类 | `utilities.assist` |
| 显示名称 | Coalesce |
| 效果 | `PURE` |

## 作用

返回第一个非空分支；关闭 Prefer Primary 时反向扫描。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Prefer Primary | BOOLEAN |
| 出 | Source | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Coalesce].Prefer Primary
[Coalesce].Source ──► [下游].…
```
