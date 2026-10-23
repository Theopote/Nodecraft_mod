# Merge Trees

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.merge` |
| 分类 | `math.data_tree` |
| 显示名称 | Merge Trees |
| 效果 | `PURE` |

## 作用

两树按匹配路径拼接项（保留 T）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | A | DATA_TREE |
| 入 | B | DATA_TREE |
| 出 | Tree | DATA_TREE |
| 出 | Branch Count | INTEGER |
| 出 | Item Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Merge Trees].A
[Merge Trees].Tree ──► [下游].…
```
