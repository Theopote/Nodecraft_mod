# Transform Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.frames.transform_frame` |
| 分类 | `reference.frames` |
| 显示名称 | Transform Frame |
| 效果 | `PURE` |

## 作用

对 FRAME 做平移，并绕**世界轴**按欧拉 **XYZ 顺序**旋转（单位：**度**）。输出仍为正交、仅朝向的架（规范化后）。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frame | `input_frame` | FRAME | 待变换架 |
| Translation | `input_translation` | VECTOR | 可选平移覆盖 |
| World Rotation X/Y/Z | `input_rot_x` 等 | DOUBLE | 绕世界轴旋转（度），欧拉 XYZ |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frame | `output_frame` | FRAME | 变换后正交架 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 是否成功 |

## 使用步骤

1. 接源 Frame（如 World Frame）。
2. 设平移向量与/或三个世界旋转角（度）。
3. 把结果接到放置链。

## 典型接法

```text
[World Frame].Frame ──► [Transform Frame].Frame
[Angle Slider].Angle ──► [Transform Frame].World Rotation Y
[Transform Frame].Frame ──► [Orient Geometry].Frame
```

## 注意事项

- 旋转是**世界轴**欧拉 XYZ，不是局部轴；顺序固定。
- 角度单位是度，与 Angle Slider 一致。
