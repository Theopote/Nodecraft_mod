# Apply Block State

| 字段 | 值 |
|---|---|
| 节点 ID | `material.block_state.apply_block_state` |
| 分类 | `material.block_state` |
| 显示名称 | Apply Block State |
| 效果 | `PURE` |

## 作用

把状态覆盖合并进已有 `BLOCK_PLACEMENT_LIST`。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Block State | BLOCK_PLACEMENT_LIST / BLOCK_STATE_DATA |
| 出 | Block Placements | BLOCK_PLACEMENT_LIST |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Apply Block State].Block Placements
[Apply Block State].Block Placements ──► [下游].…
```
