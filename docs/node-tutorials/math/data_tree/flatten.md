# Flatten Tree

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.flatten` |
| 分类 | `math.data_tree` |
| 显示名称 | Flatten Tree |
| 效果 | `PURE` |

## 作用

所有分支展平成单一列表（保留 T）。

## 示意图

```text
  Tree:  {0;0} → [a, b]
         {0;1} → [c]
         {1}   → [d, e]
           │ Flatten
           ▼
  List:  [a, b, c, d, e]   （通常按路径字典序串联）
```

```mermaid
flowchart LR
  T[DATA_TREE] --> Flat[Flatten Tree]
  Flat --> L[LIST]
  L --> Graft[Graft List]
  Graft --> T2[DATA_TREE]
```

与 [Graft List](./graft_list.md) 成对：展平丢路径结构，嫁接再拆成单元素支。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Tree | DATA_TREE |
| 出 | List | LIST |
| 出 | Item Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Flatten Tree].Tree
[Flatten Tree].List ──► [下游].…
```
