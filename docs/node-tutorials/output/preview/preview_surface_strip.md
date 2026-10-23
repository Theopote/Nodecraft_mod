# Preview Surface Strip

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_surface_strip` |
| 分类 | `output.preview` |
| 显示名称 | Preview Surface Strip |
| 效果 | `PREVIEW_WRITE` |

## 作用

预览曲面条带：截面轮廓、轨线或格子叠加（面板模式）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Surface Strip | SURFACE_STRIP |
| 出 | Success / Preview IDs / Preview Count | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Surface Strip].Surface Strip
[Preview Surface Strip].Success ──► [下游].…
```
