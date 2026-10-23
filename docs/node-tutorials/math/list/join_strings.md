# Join Strings

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.join_strings` |
| 分类 | `math.list` |
| 显示名称 | Join Strings |
| 效果 | `PURE` |

## 作用

用分隔符把 STRING_LIST 拼成一个 STRING。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | STRING_LIST |
| 入 | Separator | STRING |
| 出 | Result | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Join Strings].List
[Join Strings].Result ──► [下游].…
```
