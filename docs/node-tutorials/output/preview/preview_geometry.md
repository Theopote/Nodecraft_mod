# Preview Geometry

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_geometry` |
| 分类 | `output.preview` |
| 显示名称 | Preview Geometry |
| 效果 | `PREVIEW_WRITE` |

## 作用

实体解析面预览；Difference/Intersection 用体素幽灵方块。支持多种原语几何与 SURFACE_STRIP。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / 各原语几何 / Surface Strip | … |
| 出 | Success / Preview ID(s) / Count / Geometry / Preview Kind / Voxel Boolean* | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Geometry].Geometry
[Preview Geometry].Success ──► [下游].…
```
