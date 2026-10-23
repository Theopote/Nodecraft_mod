# Map Numbers

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.map_numbers` |
| 分类 | `math.list` |
| 显示名称 | Map Numbers |
| 效果 | `PURE` |

## 作用

对 DOUBLE_LIST 每个值做标量运算。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Numbers | DOUBLE_LIST |
| 入 | Value | DOUBLE |
| 入 | Min | DOUBLE |
| 入 | Max | DOUBLE |
| 出 | Numbers | DOUBLE_LIST |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Map Numbers].Numbers
[Map Numbers].Numbers ──► [下游].…
```
