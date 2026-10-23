# Random List Item

| 字段 | 值 |
|---|---|
| 节点 ID | `math.random.random_list_item` |
| 分类 | `math.random` |
| 显示名称 | Random List Item |
| 效果 | `PURE` |

## 作用

从列表确定性抽一项或多项（禁止重复时无放回）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | List | LIST |
| 入 | Count | INTEGER |
| 入 | Allow Duplicates | BOOLEAN |
| 入 | Seed | INTEGER |
| 出 | Item | ANY |
| 出 | Items | LIST |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Random List Item].List
[Random List Item].Item ──► [下游].…
```
