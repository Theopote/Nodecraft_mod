# Flatten List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.flatten_list` |
| 分类 | `math.list` |
| 显示名称 | Flatten List |
| 效果 | `PURE` |

## 作用

递归展平嵌套 List（有深度与元素上限）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Depth | INTEGER |
| 出 | Flattened List | LIST |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Flatten List].List
[Flatten List].Flattened List ──► [下游].…
```
