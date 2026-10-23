# Tree Statistics

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.statistics` |
| 分类 | `math.data_tree` |
| 显示名称 | Tree Statistics |
| 效果 | `PURE` |

## 作用

报告分支数、项数、深度与各支大小。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Tree | DATA_TREE |
| 出 | Branch Count | INTEGER |
| 出 | Item Count | INTEGER |
| 出 | Max Depth | INTEGER |
| 出 | Branch Sizes | INTEGER_LIST |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Tree Statistics].Tree
[Tree Statistics].Branch Count ──► [下游].…
```
