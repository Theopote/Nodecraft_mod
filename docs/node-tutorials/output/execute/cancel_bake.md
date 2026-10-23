# Cancel Bake

| 字段 | 值 |
|---|---|
| 节点 ID | `output.execute.cancel_bake` |
| 分类 | `output.execute` |
| 显示名称 | Cancel Bake |
| 效果 | `WORLD_WRITE` |

## 作用

取消当前玩家拥有的进行中 bake/apply，并回滚已写入进度。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger / Task ID | EXEC / STRING |
| 出 | Accepted / Previous State / State / Task ID / Status / Valid / Error | … |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Cancel Bake].Trigger
[Cancel Bake].Accepted ──► [下游].…
```
