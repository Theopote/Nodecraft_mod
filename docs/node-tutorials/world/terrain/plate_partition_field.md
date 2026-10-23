# Plate Partition Field

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.plate_partition_field` |
| 分类 | `world.terrain` |
| 显示名称 | Plate Partition Field |
| 效果 | `PURE` |

## 作用

伪板块 ID 与边界强度（Voronoi 式）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Seed | INTEGER |
| 入 | Plate Count | INTEGER |
| 出 | Plate Id Field | SCALAR_FIELD |
| 出 | Boundary Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Plate Partition Field].Region
[Plate Partition Field].Plate Id Field ──► [下游].…
```
