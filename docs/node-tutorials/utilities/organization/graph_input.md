# Graph Input

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.organization.graph_input` |
| 分类 | `utilities.organization` |
| 显示名称 | Graph Input |
| 效果 | `CONTEXT_READ` |

## 作用

命名图级输入，带类型化默认回退。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | Name | STRING |
| 出 | Was Provided | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Graph Input].In
[Graph Input].Name ──► [下游].…
```
