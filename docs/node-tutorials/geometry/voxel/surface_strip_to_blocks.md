# Surface Strip To Blocks

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.voxel.surface_strip_to_blocks` |
| 分类 | `geometry.voxel` |
| 显示名称 | Surface Strip To Blocks |
| 效果 | `PURE` |

## 作用

SURFACE_STRIP（或树）近似到方块格。纯转换。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Surface Strip / Surface Strip Tree | SURFACE_STRIP / DATA_TREE |
| 出 | Blocks / Blocks Tree / Region / Count / Valid | … |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Surface Strip To Blocks].Surface Strip
[Surface Strip To Blocks].Blocks ──► [下游].…
```
