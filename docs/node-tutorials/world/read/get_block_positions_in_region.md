# Get Block Positions In Region

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.get_block_positions_in_region` |
| 分类 | `world.read` |
| 显示名称 | Get Block Positions In Region |
| 效果 | `PURE` |

## 作用

生成/过滤区域内格点；超预算则均匀采样。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Coordinates | BLOCK_LIST |
| 入 | Max Points | INTEGER |
| 入 | Use Input Coordinates | BOOLEAN |
| 出 | Block Positions | BLOCK_LIST |
| 出 | Count | INTEGER |
| 出 | Total Possible | INTEGER |
| 出 | Sampled | BOOLEAN |
| 出 | Hit Limit | BOOLEAN |
| 出 | Complete | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Get Block Positions In Region].Region
[Get Block Positions In Region].Block Positions ──► [下游].…
```
