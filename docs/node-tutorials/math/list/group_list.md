# Group List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.group_list` |
| 分类 | `math.list` |
| 显示名称 | Group List |
| 效果 | `PURE` |

## 作用

按等长平行键分组为 DATA_TREE；禁止 null 项。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Group List].In
[Group List].Out ──► [下游].…
```
