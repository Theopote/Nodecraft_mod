# Scan Region By Type

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.scan_region_by_type` |
| 分类 | `world.read` |
| 显示名称 | Scan Region By Type |
| 效果 | `WORLD_READ` |

## 作用

区域按方块类型计数扫描。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Include Air | BOOLEAN |
| 入 | Target Block Type | BLOCK_TYPE |
| 入 | Max Blocks | INTEGER |
| 入 | Sample Step | INTEGER |
| 出 | Block Type IDs | STRING_LIST |
| 出 | Counts | INTEGER_LIST |
| 出 | Total Scanned | INTEGER |
| 出 | Total Possible | INTEGER |
| 出 | Unique Type Count | INTEGER |
| 出 | Most Common Block | STRING |
| 出 | Most Common Count | INTEGER |
| 出 | Target Count | INTEGER |
| 出 | Hit Limit | BOOLEAN |
| 出 | Complete | BOOLEAN |
| 出 | Stopped Reason | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Scan Region By Type].Region
[Scan Region By Type].Block Type IDs ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
