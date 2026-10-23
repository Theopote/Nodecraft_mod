# Voxelize Geometry

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.voxel.voxelize_geometry` |
| 分类 | `geometry.voxel` |
| 显示名称 | Voxelize Geometry |
| 效果 | `PURE` |

## 作用

GEOMETRY（或 Geometry Tree）→ `BLOCK_LIST`。纯转换，不写世界。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Geometry Tree | GEOMETRY / DATA_TREE |
| 出 | Blocks / Blocks Tree / Region / Count / Valid / Error / Status | … |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[Difference].Geometry ──► [Voxelize Geometry].Geometry
[Voxelize].Blocks ──► Apply Changes
```
