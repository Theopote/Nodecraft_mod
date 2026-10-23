# Cull Empty Branches

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.cull_empty` |
| 分类 | `math.data_tree` |
| 显示名称 | Cull Empty Branches |
| 效果 | `PURE` |

## 作用

删除空分支（保留 T）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Tree | DATA_TREE |
| 出 | Tree | DATA_TREE |
| 出 | Removed Count | INTEGER |
| 出 | Branch Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Cull Empty Branches].Tree
[Cull Empty Branches].Tree ──► [下游].…
```
