# Construct Tree Path

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.tree_path` |
| 分类 | `math.data_tree` |
| 显示名称 | Construct Tree Path |
| 效果 | `PURE` |

## 作用

由非负整数索引有序列表构造 TREE_PATH。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Indices | INTEGER_LIST |
| 出 | Path | TREE_PATH |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Construct Tree Path].Indices
[Construct Tree Path].Path ──► [下游].…
```
