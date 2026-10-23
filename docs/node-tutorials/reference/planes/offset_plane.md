# Offset Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.planes.offset_plane` |
| 分类 | `reference.planes` |
| 显示名称 | Offset Plane |
| 效果 | `PURE` |

## 作用

沿平面法线按**有符号距离**平移原点，得到平行平面。正距离沿法线方向，负距离相反。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Plane | `input_plane` | PLANE | 源平面（须 canonical） |
| Distance | `input_distance` | DOUBLE | 有符号偏移；未连接默认 `0`；连了非法则 fail-closed |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Plane | `output_plane` | PLANE | 偏移后平面 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 是否成功 |

## 使用步骤

1. 接源 Plane。
2. 设 Distance（可用 Number Slider）。
3. 把新 Plane 接到镜像、截面、放置参照。

## 典型接法

```text
[World Plane].Plane ──► [Offset Plane].Plane
[Number Slider].Value ──► [Offset Plane].Distance
[Offset Plane].Plane ──► [Mirror Geometry About Plane].Plane
```

## 注意事项

- 源平面必须是 canonical；Distance 连接后非法 → Valid false。
- 极大 Distance 可能导致原点非有限而失败。
