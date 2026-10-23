# Delta Accumulate Field

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.delta_accumulate_field` |
| 分类 | `world.terrain` |
| 显示名称 | Delta Accumulate Field |
| 效果 | `PURE` |

## 作用

应用/回退增量并累加地形 delta。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Base Height Field | SCALAR_FIELD |
| 入 | Delta Field | SCALAR_FIELD |
| 入 | Accumulated Delta Field | SCALAR_FIELD |
| 入 | Strength | DOUBLE |
| 出 | Height Field | SCALAR_FIELD |
| 出 | Delta Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Delta Accumulate Field].Base Height Field
[Delta Accumulate Field].Height Field ──► [下游].…
```
