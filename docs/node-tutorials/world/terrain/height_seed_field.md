# Height Seed Field

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.height_seed_field` |
| 分类 | `world.terrain` |
| 显示名称 | Height Seed Field |
| 效果 | `PURE` |

## 作用

大陆尺度确定性种子高度场 X/Z（归一化 [-1,1]）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Seed | INTEGER |
| 入 | Scale Km | DOUBLE |
| 入 | Continent Bias | DOUBLE |
| 出 | Height Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Height Seed Field].Region
[Height Seed Field].Height Field ──► [下游].…
```
