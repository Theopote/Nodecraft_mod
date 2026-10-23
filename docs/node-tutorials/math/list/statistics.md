# List Statistics

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.statistics` |
| 分类 | `math.list` |
| 显示名称 | List Statistics |
| 效果 | `PURE` |

## 作用

计算 DOUBLE_LIST 的 min、max、sum、平均与中位数。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Numbers | DOUBLE_LIST |
| 出 | Min | DOUBLE |
| 出 | Max | DOUBLE |
| 出 | Sum | DOUBLE |
| 出 | Average | DOUBLE |
| 出 | Median | DOUBLE |
| 出 | Count | INTEGER |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [List Statistics].Numbers
[List Statistics].Min ──► [下游].…
```
