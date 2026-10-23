# Tree Branch

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.branch` |
| 分类 | `math.data_tree` |
| 显示名称 | Tree Branch |
| 效果 | `PURE` |

## 作用

按 TREE_PATH 取一支（保留 T）；路径不存在则 Found=false。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Tree | DATA_TREE |
| 入 | Path | TREE_PATH |
| 出 | Branch | LIST |
| 出 | Found | BOOLEAN |
| 出 | Item Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Tree Branch].Tree
[Tree Branch].Branch ──► [下游].…
```
