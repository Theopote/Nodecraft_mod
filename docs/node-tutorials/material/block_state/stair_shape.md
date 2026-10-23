# Stair Shape

| 字段 | 值 |
|---|---|
| 节点 ID | `material.block_state.stair_shape` |
| 分类 | `material.block_state` |
| 显示名称 | Stair Shape |
| 效果 | `PURE` |

## 作用

根据相邻楼梯放置解析转角 shape（inner/outer 等）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Block Placements / Direction / Half | BLOCK_PLACEMENT_LIST / VECTOR / STRING |
| 出 | Block Placements | BLOCK_PLACEMENT_LIST |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入占用源（放置列表 / 坐标 / 几何之一）与材质参数。
2. 输出 Block Placements 接到 Preview 或 Apply Changes。

## 典型接法

```text
[上游].… ──► [Stair Shape].Block Placements
[Stair Shape].Block Placements ──► [下游].…
```
