# Lloyd Relax 3D

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.voronoi_3d.lloyd_relax` |
| 分类 | `pattern.voronoi_3d` |
| 显示名称 | Lloyd Relax 3D |
| 效果 | `PURE` |

## 作用

轴对齐盒内用均匀采样格近似 Lloyd 松弛。**不是**精确 Voronoi 图。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Sites / Corner A / Corner B / Cells / Iterations | POINT_LIST / POINT / INTEGER |
| 出 | Sites / Count | POINT_LIST / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Lloyd Relax 3D].Sites
[Lloyd Relax 3D].Sites ──► [下游].…
```
