# Sort Text

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.sort_text` |
| 分类 | `math.list` |
| 显示名称 | Sort Text |
| 效果 | `PURE` |

## 作用

STRING_LIST 升序或降序排序。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Texts | STRING_LIST |
| 出 | Texts | STRING_LIST |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Sort Text].Texts
[Sort Text].Texts ──► [下游].…
```
