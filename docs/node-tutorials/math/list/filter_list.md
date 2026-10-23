# Filter List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.filter_list` |
| 分类 | `math.list` |
| 显示名称 | Filter List |
| 效果 | `PURE` |

## 作用

用等长 BOOLEAN_LIST 掩码过滤列表（保留 T）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Mask | BOOLEAN_LIST |
| 出 | Filtered | LIST |
| 出 | Removed | LIST |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Filter List].List
[Filter List].Filtered ──► [下游].…
```
