# Construct Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.frames.construct_frame` |
| 分类 | `reference.frames` |
| 显示名称 | Construct Frame |
| 效果 | `PURE` |

## 作用

由 **Origin**、**X Axis**、**Y Axis** 构造右手正交 FRAME；**Z = X × Y**（再正交化）。未连接的口有默认：原点世界零点，X=+X，Y=+Y。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Origin | `input_origin` | POINT | 可选；默认 `(0,0,0)` |
| X Axis | `input_x_axis` | VECTOR | 可选；默认世界 +X |
| Y Axis | `input_y_axis` | VECTOR | 可选；默认世界 +Y |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frame | `output_frame` | FRAME | 正交右手架 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 轴是否可用 |

## 使用步骤

1. 接 Origin（如 Player Position）。
2. （可选）接 X/Y 方向；共线或零向量会失败。
3. 把 Frame 接到放置节点。

## 典型接法

```text
[Player Position].Position ──► [Construct Frame].Origin
[Vector Input].Vector      ──► [Construct Frame].X Axis
[Construct Frame].Frame    ──► [Column / 放置].Frame
```

## 注意事项

- 输出是正交化后的架，输入 X/Y 不必事先单位正交，但不能退化（平行 / 零）。
