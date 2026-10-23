# Sub List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.sub_list` |
| 分类 | `math.list` |
| 显示名称 | Sub List |
| 效果 | `PURE` |

## 作用

含起不含止切片；负从末尾；越界 Valid=false。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Start | INTEGER |
| 入 | End | INTEGER |
| 出 | Sub List | LIST |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Sub List].List
[Sub List].Sub List ──► [下游].…
```
