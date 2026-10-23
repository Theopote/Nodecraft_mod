# Orogenic Uplift Field

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.orogenic_uplift_field` |
| 分类 | `world.terrain` |
| 显示名称 | Orogenic Uplift Field |
| 效果 | `PURE` |

## 作用

边界强度 → 造山抬升势。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Boundary Field | SCALAR_FIELD |
| 入 | Strength | DOUBLE |
| 入 | Falloff | DOUBLE |
| 出 | Uplift Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Orogenic Uplift Field].Boundary Field
[Orogenic Uplift Field].Uplift Field ──► [下游].…
```
