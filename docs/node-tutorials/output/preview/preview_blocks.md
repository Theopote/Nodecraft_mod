# Preview Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_blocks` |
| 分类 | `output.preview` |
| 显示名称 | Preview Blocks |
| 效果 | `PREVIEW_WRITE` |

## 作用

以临时幽灵方块预览坐标、放置或放置树。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Blocks / Block Placements / Tree / Block Type | … |
| 出 | Success / Preview ID / Block·Source·Preview Count / Truncated / Error | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Blocks].Blocks
[Preview Blocks].Success ──► [下游].…
```
