# Set Item

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.set_item` |
| 分类 | `math.list` |
| 显示名称 | Set Item |
| 效果 | `PURE` |

## 作用

在索引处设值（负从末尾）；非法则 Valid=false、空列表。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Index | INTEGER |
| 入 | Value | ANY |
| 出 | List | LIST |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Set Item].List
[Set Item].List ──► [下游].…
```
