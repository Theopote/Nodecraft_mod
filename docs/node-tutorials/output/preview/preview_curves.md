# Preview Curves

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_curves` |
| 分类 | `output.preview` |
| 显示名称 | Preview Curves |
| 效果 | `PREVIEW_WRITE` |

## 作用

预览线/折线/曲线路径。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Path / Paths / Points | PATH / PATH_LIST / POINT_LIST |
| 出 | Success / Preview IDs / Preview Count | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Curves].Path
[Preview Curves].Success ──► [下游].…
```
