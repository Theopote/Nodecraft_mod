# Get Entities In Region

| 字段 | 值 |
|---|---|
| 节点 ID | `world.query.get_entities_in_region` |
| 分类 | `world.query` |
| 显示名称 | Get Entities In Region |
| 效果 | `WORLD_READ` |

## 作用

区域内实体（可过滤玩家/物品）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Entity Type | ENTITY_TYPE |
| 入 | Exclude Players | BOOLEAN |
| 入 | Include Items | BOOLEAN |
| 出 | Entities List | MINECRAFT_ENTITY_LIST |
| 出 | Count | INTEGER |
| 出 | Player Count | INTEGER |
| 出 | Nearest Entity | MINECRAFT_ENTITY |
| 出 | Entity UUIDs | STRING_LIST |
| 出 | Entity Type IDs | STRING_LIST |
| 出 | Entity Positions | POINT_LIST |
| 出 | Item Count | INTEGER |
| 出 | Complete | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Entities In Region].Region
[Get Entities In Region].Entities List ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
