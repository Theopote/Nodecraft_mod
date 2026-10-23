# Subgraph

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.organization.subgraph` |
| 分类 | `utilities.organization` |
| 显示名称 | Subgraph |
| 效果 | `COMPOSITE` |

## 作用

执行引用的子图定义（类型化接口端口）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Enabled | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Subgraph].Enabled
[Subgraph].Out ──► [下游].…
```
