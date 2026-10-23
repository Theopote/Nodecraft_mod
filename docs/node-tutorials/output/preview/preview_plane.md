# Preview Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_plane` |
| 分类 | `output.preview` |
| 显示名称 | Preview Plane |
| 效果 | `PREVIEW_WRITE` |

## 作用

平面预览：方格 + 可选轴与法线。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Plane / Grid Size / Grid Spacing / Show Axes / Show Normal | PLANE / DOUBLE / BOOLEAN |
| 出 | Success / Preview ID | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Plane].Plane
[Preview Plane].Success ──► [下游].…
```
