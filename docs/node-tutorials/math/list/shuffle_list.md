# Shuffle List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.shuffle_list` |
| 分类 | `math.list` |
| 显示名称 | Shuffle List |
| 效果 | `PURE` |

## 作用

用 Seed 确定性打乱（保留 T）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Seed | INTEGER |
| 出 | Shuffled | LIST |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Shuffle List].List
[Shuffle List].Shuffled ──► [下游].…
```
