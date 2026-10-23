# Sample Field On Region

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.sample_field_on_region` |
| 分类 | `world.terrain` |
| 显示名称 | Sample Field On Region |
| 效果 | `PURE` |

## 作用

区域内规则 X/Z 格心采样标量场。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Field | SCALAR_FIELD |
| 入 | Step | INTEGER |
| 入 | Max Samples | INTEGER |
| 出 | Sample Points | POINT_LIST |
| 出 | Values | DOUBLE_LIST |
| 出 | Count | INTEGER |
| 出 | Step Used | INTEGER |
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
[上游].… ──► [Sample Field On Region].Region
[Sample Field On Region].Sample Points ──► [下游].…
```
