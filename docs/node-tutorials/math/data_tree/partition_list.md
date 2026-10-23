# Partition List To Tree

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.partition_list` |
| 分类 | `math.data_tree` |
| 显示名称 | Partition List To Tree |
| 效果 | `PURE` |

## 作用

按固定大小拆成树分支（保留末尾不完整支；保留 T）。

## 示意图

```text
  List:  [a b c d e f g]   Size=3
           │ Partition
           ▼
  Tree:  {0} → [a b c]
         {1} → [d e f]
         {2} → [g]        ← 末支可短于 Size
```

```mermaid
flowchart LR
  L[LIST] --> Part[Partition List]
  Part --> T[DATA_TREE 定长支]
```

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Size | INTEGER |
| 出 | Tree | DATA_TREE |
| 出 | Branch Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Partition List To Tree].List
[Partition List To Tree].Tree ──► [下游].…
```
