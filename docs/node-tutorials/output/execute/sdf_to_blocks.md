# SDF To Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `output.execute.sdf_to_blocks` |
| 分类 | `output.execute` |
| 显示名称 | SDF To Blocks |
| 效果 | `WORLD_WRITE` |

## 作用

把 SDF 直接体素化为方块坐标（Bounds / Iso / Padding）。输出 Blocks，需再 Apply 才落世界（或看节点是否内写——效果为 WORLD_WRITE 时以源码为准）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | SDF / Bounds Min·Max / Iso Value / Padding | SDF / POINT / DOUBLE |
| 出 | Blocks / Region / Geometry / Count / Valid | … |

## 使用步骤

1. 准备数据输入（放置/几何/坐标等）。
2. 用 EXEC/Trigger 触发（若有）；确认 Success / Status。
3. 需要时接 Undo 或 Bake Status 做收尾。

## 典型接法

```text
[上游].… ──► [SDF To Blocks].SDF
[SDF To Blocks].Blocks ──► [下游].…
```
