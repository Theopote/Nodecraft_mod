# Construct Plane From Points

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.planes.plane_from_points` |
| 分类 | `reference.planes` |
| 显示名称 | Construct Plane From Points |
| 效果 | `PURE` |

## 作用

用三个**不共线**的几何点 A、B、C 定平面。适合从拾取点或几何角点建参照面。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Point A / B / C | `input_point_a` 等 | POINT | 平面上三点 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Plane | `output_plane` | PLANE | 结果 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 三点是否有效 |

## 使用步骤

1. 接三个不共线点（避免共线或重合）。
2. Valid 为 true 后把 Plane 接到轮廓 / 镜像等。

## 典型接法

```text
[三点来源].A/B/C ──► [Construct Plane From Points]
                 ──► [Frame From Plane] / Profiles
```

## 注意事项

- 共线或重复点 → Valid false。
- 法线方向由点序决定（右手系惯例，以实现为准）。
