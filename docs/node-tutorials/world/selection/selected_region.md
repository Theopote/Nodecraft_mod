# Selected Region

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.selected_region` |
| 分类 | `world.selection` |
| 显示名称 | Selected Region |
| 效果 | `CONTEXT_READ` |

## 作用

玩家两点角点定义的选区。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | Region | REGION |
| 出 | Min Block | BLOCK_POS |
| 出 | Max Block | BLOCK_POS |
| 出 | Has Selection | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Selected Region].In
[Selected Region].Region ──► [下游].…
```
