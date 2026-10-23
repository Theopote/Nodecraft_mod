# Create List

| 字段 | 值 |
|---|---|
| 节点 ID | `math.list.create_list` |
| 分类 | `math.list` |
| 显示名称 | Create List |
| 效果 | `PURE` |

## 作用

把多个 ANY 装入异构 LIST（不是类型化 LIST<T> 构造器）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | List | LIST |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Create List].In
[Create List].List ──► [下游].…
```
