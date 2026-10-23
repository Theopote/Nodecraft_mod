# Validate

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.assist.validate` |
| 分类 | `utilities.assist` |
| 显示名称 | Validate |
| 效果 | `PURE` |

## 作用

校验布尔条件并门控透传值。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Condition | BOOLEAN |
| 入 | Message | STRING |
| 出 | Message | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Validate].Condition
[Validate].Message ──► [下游].…
```
