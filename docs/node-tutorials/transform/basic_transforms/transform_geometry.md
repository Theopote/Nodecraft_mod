# Transform Geometry

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.basic_transforms.transform_geometry` |
| 分类 | `transform.basic_transforms` |
| 显示名称 | Transform Geometry |
| 效果 | `PURE` |

## 作用

平移 + Euler XYZ 旋转（度）+ 均匀缩放，一次作用到解析几何（原语/复合/布尔/SDF 包装）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Translation / Rotation X·Y·Z / Scale | … |
| 出 | Geometry | GEOMETRY |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Transform Geometry].Geometry
[Transform Geometry].Geometry ──► [下游].…
```
