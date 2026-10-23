# Deconstruct Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.frames.deconstruct_frame` |
| 分类 | `reference.frames` |
| 显示名称 | Deconstruct Frame |
| 效果 | `PURE` |

## 作用

拆单个 FRAME：Origin、X/Y/Z 轴，以及由原点 + Z 轴得到的 Plane。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frame | `input_frame` | FRAME | 待拆架 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Origin | `output_origin` | POINT | 原点 |
| X / Y / Z Axis | `output_x_axis` 等 | VECTOR | 三轴 |
| Plane | `output_plane` | PLANE | 原点 + Z 轴平面 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 是否为可用正交架 |

## 使用步骤

1. 接任意 FRAME。
2. 用轴做向量运算，或用 Plane 接剖面节点。

## 典型接法

```text
[Construct Frame].Frame ──► [Deconstruct Frame]
[Deconstruct Frame].Z Axis ──► [Normalize / Offset]
[Deconstruct Frame].Plane ──► [Profiles]
```

## 注意事项

- 非可用正交架 → Valid false。
