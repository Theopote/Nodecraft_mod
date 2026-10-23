# Selected Block Sequence

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.selected_block_sequence` |
| 分类 | `world.selection` |
| 显示名称 | Selected Block Sequence |
| 效果 | `CONTEXT_READ` |

## 作用

按点击顺序收集多选方块序列。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | Blocks | BLOCK_LIST |
| 出 | Centers | POINT_LIST |
| 出 | Path | PATH |
| 出 | First | BLOCK_POS |
| 出 | Last | BLOCK_POS |
| 出 | Count | INTEGER |
| 出 | Is Closed | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Selected Block Sequence].In
[Selected Block Sequence].Blocks ──► [下游].…
```
