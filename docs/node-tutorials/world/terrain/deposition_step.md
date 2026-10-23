# Deposition Step

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.deposition_step` |
| 分类 | `world.terrain` |
| 显示名称 | Deposition Step |
| 效果 | `PURE` |

## 作用

低坡/低能区沉积一步。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Region | REGION |
| 入 | Height Field | SCALAR_FIELD |
| 入 | Sediment Field | SCALAR_FIELD |
| 入 | Slope Field | SCALAR_FIELD |
| 入 | Accumulation Field | SCALAR_FIELD |
| 入 | Capacity | DOUBLE |
| 入 | Rate | DOUBLE |
| 出 | Height Field | SCALAR_FIELD |
| 出 | Sediment Field | SCALAR_FIELD |
| 出 | Delta Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Deposition Step].Region
[Deposition Step].Height Field ──► [下游].…
```
