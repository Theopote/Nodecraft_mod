# Apply Frame Orientation

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.placement.orient_geometry_to_frame` |
| 分类 | `transform.placement` |
| 显示名称 | Apply Frame Orientation |
| 效果 | `PURE` |

## 作用

相对现有坐标应用 FRAME 旋转，**Pivot 世界位置不变**（不是绝对“放到 Frame”）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Pivot / Frame | GEOMETRY / POINT / FRAME |
| 出 | Geometry | GEOMETRY |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Apply Frame Orientation].Geometry
[Apply Frame Orientation].Geometry ──► [下游].…
```
