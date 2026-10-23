# Flow Direction Field

| 字段 | 值 |
|---|---|
| 节点 ID | `world.terrain.flow_direction_field` |
| 分类 | `world.terrain` |
| 显示名称 | Flow Direction Field |
| 效果 | `PURE` |

## 作用

高度场下坡流向与坡度。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Height Field | SCALAR_FIELD |
| 入 | Step | DOUBLE |
| 出 | Flow Field | VECTOR_FIELD |
| 出 | Slope Field | SCALAR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Flow Direction Field].Height Field
[Flow Direction Field].Flow Field ──► [下游].…
```
