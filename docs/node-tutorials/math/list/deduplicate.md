# Deduplicate List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.deduplicate` |
| 分类 | `math.list` |
| 显示名称 | Deduplicate List |
| 效果 | `PURE` |

## 作用

去重并保留首次出现顺序（保留 T）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 出 | Unique | LIST |
| 出 | Removed | LIST |
| 出 | Unique Count | INTEGER |
| 出 | Removed Count | INTEGER |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Deduplicate List].List
[Deduplicate List].Unique ──► [下游].…
```
