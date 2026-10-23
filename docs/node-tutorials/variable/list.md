# Variable List

| 字段 | 值 |
|---|---|
| 节点 ID | `variable.list` |
| 分类 | `variable` |
| 显示名称 | Variable List |
| 效果 | `CONTEXT_READ` |

## 作用

列出当前执行作用域中的用户变量。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Prefix | STRING |
| 出 | Names | STRING_LIST |
| 出 | Values | LIST |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Variable List].Prefix
[Variable List].Names ──► [下游].…
```
