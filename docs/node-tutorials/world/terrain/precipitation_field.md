# Precipitation Field

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.precipitation_field` |
| 分类 | `world.terrain` |
| 显示名称 | Precipitation Field |
| 效果 | `PURE` |

## 作用

纬度带 + 雨影响应降水场。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Height Field | SCALAR_FIELD |
| 入 | Wind Field | VECTOR_FIELD |
| 入 | Equator Z | DOUBLE |
| 入 | Rain Base | DOUBLE |
| 出 | Rain Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Precipitation Field].Region
[Precipitation Field].Rain Field ──► [下游].…
```
