# Max Number

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.max_number` |
| 分类 | `math.list` |
| 显示名称 | Max Number |
| 效果 | `PURE` |

## 作用

DOUBLE_LIST 最大值。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Numbers | DOUBLE_LIST |
| 出 | Max | DOUBLE |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Max Number].Numbers
[Max Number].Max ──► [下游].…
```
