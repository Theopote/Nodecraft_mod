# Dispatch List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.dispatch_list` |
| 分类 | `math.list` |
| 显示名称 | Dispatch List |
| 效果 | `PURE` |

## 作用

用等长 BOOLEAN_LIST 掩码拆分列表（保留 T）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Mask | BOOLEAN_LIST |
| 出 | True List | LIST |
| 出 | False List | LIST |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Dispatch List].List
[Dispatch List].True List ──► [下游].…
```
