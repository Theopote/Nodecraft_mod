# Geometry Viewer

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.geometry_viewer` |
| 分类 | `output.preview` |
| 显示名称 | Geometry Viewer |
| 效果 | `PREVIEW_WRITE` |

## 作用

可视化预览几何/方块，**不写入世界**。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Blocks / Geometry / 原语几何 / Block Type / Preview Color / Transparency | … |
| 出 | Blocks / Block Count | BLOCK_LIST / INTEGER |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Geometry Viewer].Blocks
[Geometry Viewer].Blocks ──► [下游].…
```
