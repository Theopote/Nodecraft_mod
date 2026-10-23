# Selected Block

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.selected_block` |
| 分类 | `world.selection` |
| 显示名称 | Selected Block |
| 效果 | `CONTEXT_READ` |

## 作用

编辑器选中方块源：位置 / Has Selection。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | X | INTEGER |
| 入 | Y | INTEGER |
| 入 | Z | INTEGER |
| 出 | Block Position | BLOCK_POS |
| 出 | Has Selection | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Selected Block].X
[Selected Block].Block Position ──► [下游].…
```
