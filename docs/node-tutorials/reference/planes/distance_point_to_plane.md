# Distance Point To Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.planes.distance_point_to_plane` |
| 分类 | `reference.planes` |
| 显示名称 | Distance Point To Plane |
| 效果 | `PURE` |

## 作用

计算几何点到平面的**绝对距离**与**有符号距离**（沿法线一侧为正，具体符号以实现的 `signedDistanceTo` 为准）。用于贴地检测、厚度判断、裁剪条件。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Point | `input_point` | POINT | 待测点 |
| Plane | `input_plane` | PLANE | 参照平面（须 canonical） |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Distance | `output_distance` | DOUBLE | 绝对距离 \|signed\| |
| Signed Distance | `output_signed_distance` | DOUBLE | 有符号距离 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 输入是否有效 |

## 使用步骤

1. 接 Point 与 Plane。
2. 用 Absolute Distance 做「多远」；用 Signed 做「在哪一侧」。
3. 接到 Compare / Branch。

## 典型接法

```text
[某点].Point + [World Plane].Plane ──► [Distance Point To Plane]
[Distance].Signed Distance ──► [Compare] ──► [Branch]
```

## 注意事项

- 失败时 Distance / Signed 会写成 `0`，务必看 **Valid**，不要只看数值。
