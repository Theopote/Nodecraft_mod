# Orient Block State

| 字段 | 值 |
|---|---|
| 节点 ID | `material.block_state.orient_block_state` |
| 分类 | `material.block_state` |
| 显示名称 | Orient Block State |
| 效果 | `PURE` |

## 作用

由方向向量推导 facing / axis / stair half 等状态属性。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Base State / Block Type / Vector / Mode / Include Waterlogged / Waterlogged | … |
| 出 | Block State / Facing / Axis / Half | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Orient Block State].Base State
[Orient Block State].Block State ──► [下游].…
```
