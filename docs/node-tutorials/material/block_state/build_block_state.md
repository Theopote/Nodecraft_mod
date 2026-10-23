# Build Block State

| 字段 | 值 |
|---|---|
| 节点 ID | `material.block_state.build_block_state` |
| 分类 | `material.block_state` |
| 显示名称 | Build Block State |
| 效果 | `PURE` |

## 作用

由 Block Type、可选 Base State 与属性覆盖，构建并校验 `BLOCK_STATE_DATA`。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Base State / Block Type / Property·Value / Facing / Axis / Half / Waterlogged | … |
| 出 | Block State / Property Count | BLOCK_STATE_DATA / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Build Block State].Base State
[Build Block State].Block State ──► [下游].…
```
