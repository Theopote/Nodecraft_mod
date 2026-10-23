# Grid Array

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.grid.grid_array` |
| 分类 | `pattern.grid` |
| 显示名称 | Grid Array |
| 效果 | `PURE` |

## 作用

2D/3D 格点阵列复制几何。自定义 X/Y/Z Direction 可成斜格。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / X·Y·Z Direction·Distance·Count | GEOMETRY / VECTOR / DOUBLE / INTEGER |
| 出 | Geometry / Offsets / Geometry Tree / Offset Tree / Count | … |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[Box].Geometry ──► [Grid Array].Geometry
[Grid Array].Geometry ──► Voxelize
```
