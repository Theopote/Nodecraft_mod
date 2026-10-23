# Bake Status

| 字段 | 值 |
|---|---|
| 节点 ID | `output.execute.bake_status` |
| 分类 | `output.execute` |
| 显示名称 | Bake Status |
| 效果 | `CONTEXT_READ` |

## 作用

按当前玩家拥有的 Task ID 查询 BakePlacementService：状态、进度、已放/跳过/回滚失败计数。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Task ID | STRING |
| 出 | Found / State / Progress / Placed / Skipped / Rollback Failed / Total / Remaining / Status | … |
| 出 | Valid / Error | BOOLEAN / STRING |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Bake Status].Task ID
[Bake Status].Found ──► [下游].…
```
