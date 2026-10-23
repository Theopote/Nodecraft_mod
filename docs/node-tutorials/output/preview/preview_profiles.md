# Preview Profiles

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_profiles` |
| 分类 | `output.preview` |
| 显示名称 | Preview Profiles |
| 效果 | `PREVIEW_WRITE` |

## 作用

预览多边形轮廓边界与可选法线指示。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Profile / Profiles | POLYGON_PROFILE / LIST |
| 出 | Success / Preview IDs / Preview Count | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Profiles].Profile
[Preview Profiles].Success ──► [下游].…
```
