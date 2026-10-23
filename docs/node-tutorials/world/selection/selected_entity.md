# Selected Entity

| 字段 | 值 |
|---|---|
| 节点 ID | `world.selection.selected_entity` |
| 分类 | `world.selection` |
| 显示名称 | Selected Entity |
| 效果 | `CONTEXT_READ` |

## 作用

编辑器选中实体（UUID/类型/点/格/活实体）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 出 | UUID | STRING |
| 出 | Entity Type | ENTITY_TYPE |
| 出 | Entity | MINECRAFT_ENTITY |
| 出 | Block Position | BLOCK_POS |
| 出 | Exact Position | POINT |
| 出 | Distance To Player | DOUBLE |
| 出 | Has Entity | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Selected Entity].In
[Selected Entity].UUID ──► [下游].…
```
