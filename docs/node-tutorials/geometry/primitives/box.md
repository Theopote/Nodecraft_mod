# Box by Center + Size

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.box` |
| 分类 | `geometry.primitives` |
| 显示名称 | Box by Center + Size |
| 效果 | `PURE` |

## 作用

中心点 + 局部 X/Y/Z 尺寸构造连续盒体；可选 Plane 与局部旋转（度）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center | `input_center` | POINT（可选，有面板默认） |
| 入 | Plane | `input_plane` | PLANE（可选，定向） |
| 入 | Size X/Y/Z | `input_size_x` 等 | DOUBLE，须 > 0 |
| 入 | Rotation X/Y/Z | `input_rot_x` 等 | DOUBLE（度，可选） |
| 出 | Geometry | `output_geometry` | GEOMETRY |
| 出 | Box Geometry | `output_box_geometry` | BOX_GEOMETRY |
| 出 | Corners | `output_corners` | POINT_LIST（8 角） |
| 出 | Faces | `output_faces` | BOX_FACE_LIST（6 面） |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[Construct Point] ──► [Box].Center
[World Plane] ──► [Box].Plane
[Box].Geometry ──► 体素化 / 布尔
```

## 注意事项

- Size 须有限且 > 0。Plane 或旋转非零时为定向盒。
