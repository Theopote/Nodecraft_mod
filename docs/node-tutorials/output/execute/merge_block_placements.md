# Merge Block Placements

| 字段 | 值 |
|---|---|
| 节点 ID | `output.execute.merge_block_placements` |
| 分类 | `output.execute` |
| 显示名称 | Merge Block Placements |
| 效果 | `PURE` |

## 作用

合并多路放置列表/树为可执行放置（去重、冲突统计）。输入口数量可调（类似 Combine）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | 多路放置 / 树（面板配置） | BLOCK_PLACEMENT_LIST / DATA_TREE |
| 出 | Block Placements / Tree / Positions / Block IDs / Count / Duplicate·Conflict·Input·Skipped Invalid Count | … |
| 出 | Valid / Error | BOOLEAN / STRING |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Merge Block Placements].多路放置
[Merge Block Placements].Block Placements ──► [下游].…
```
