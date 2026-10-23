# Graft List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.graft_list` |
| 分类 | `math.data_tree` |
| 显示名称 | Graft List |
| 效果 | `PURE` |

## 作用

列表每项各自成数据树分支（保留元素类型 T）。

## 示意图

```text
  List:  [a, b, c]
           │ Graft
           ▼
  Tree:  {0} → [a]
         {1} → [b]
         {2} → [c]
```

与 Flatten 相反：Graft 把「一条列表」拆成「多项各一支」；Flatten 把多支压回一条列表。

```mermaid
flowchart LR
  L[LIST] --> Graft[Graft List]
  Graft --> T[DATA_TREE 每项一支]
  T --> Flat[Flatten Tree]
  Flat --> L2[LIST]
```

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 出 | Tree | DATA_TREE |
| 出 | Branch Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Graft List].List
[Graft List].Tree ──► [下游].…
```
