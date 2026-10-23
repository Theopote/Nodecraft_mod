# Clear Preview

| 字段 | 值 |
|---|---|
| 节点 ID | `output.execute.clear_preview` |
| 分类 | `output.execute` |
| 显示名称 | Clear Preview |
| 效果 | `PREVIEW_WRITE` |

## 作用

清除全部活动预览。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger | ANY |
| 出 | Success / Cleared Count | BOOLEAN / INTEGER |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [Clear Preview].Trigger
[Clear Preview].Success ──► [下游].…
```
