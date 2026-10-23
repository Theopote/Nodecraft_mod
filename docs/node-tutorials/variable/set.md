# Set Variable

| 字段 | 值 |
|---|---|
| 节点 ID | `variable.set` |
| 分类 | `variable` |
| 显示名称 | Set Variable |
| 效果 | `CONTEXT_WRITE` |

## 作用

按名写入类型化用户变量；槽类型首次写入固定，类型不匹配则失败。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Name | STRING |
| 出 | Name | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Exists Before | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Set Variable].Name
[Set Variable].Name ──► [下游].…
```
