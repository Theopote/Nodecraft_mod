# Graph Output

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.organization.graph_output` |
| 分类 | `utilities.organization` |
| 显示名称 | Graph Output |
| 效果 | `CONTEXT_WRITE` |

## 作用

命名图级输出，发布到当前调用帧。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | Name | STRING |
| 出 | Error | STRING |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Graph Output].In
[Graph Output].Name ──► [下游].…
```
