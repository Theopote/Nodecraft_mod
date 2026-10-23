# Preview Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_frame` |
| 分类 | `output.preview` |
| 显示名称 | Preview Frame |
| 效果 | `PREVIEW_WRITE` |

## 作用

预览局部坐标系（X/Y/Z）。可用 Frame，或 Origin+Plane+轴分量拼装。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Frame / Origin / Plane / X·Y·Z Axis | FRAME / POINT / PLANE / VECTOR |
| 出 | Success / Preview ID / Status | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Frame].Frame
[Preview Frame].Success ──► [下游].…
```
