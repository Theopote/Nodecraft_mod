# Shift Path

| 字段 | 值 |
|---|---|
| 节点 ID | `math.data_tree.shift_path` |
| 分类 | `math.data_tree` |
| 显示名称 | Shift Path |
| 效果 | `PURE` |

## 作用

路径上移（去掉前导层）或下移（补 0 层）；冲突路径合并项。

## 示意图

```text
  原树:  {0;1;2} → [a]
         {0;1;3} → [b]

  Shift -1（上移）:  {1;2} → [a]
                     {1;3} → [b]

  Shift +1（下移）:  {0;0;1;2} → [a]
                     {0;0;1;3} → [b]

  冲突时同路径项合并
```

```mermaid
flowchart LR
  T[DATA_TREE] --> Shift[Shift Path]
  Shift --> T2[路径升降后的树]
```

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Tree | DATA_TREE |
| 入 | Shift | INTEGER |
| 出 | Tree | DATA_TREE |
| 出 | Branch Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Shift Path].Tree
[Shift Path].Tree ──► [下游].…
```
