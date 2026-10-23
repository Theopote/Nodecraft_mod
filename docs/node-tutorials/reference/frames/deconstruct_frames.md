# Deconstruct Frames

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.frames.deconstruct_frames` |
| 分类 | `reference.frames` |
| 显示名称 | Deconstruct Frames |
| 效果 | `PURE` |

## 作用

拆 **FRAME_LIST**：输出 Origins / 各轴列表 / Planes 列表与 Count。适合阵列放置后的批量处理。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frames | `input_frames` | FRAME_LIST | 架列表 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Origins | `output_origins` | POINT_LIST | 原点列表 |
| X / Y / Z Axes | `output_x_axes` 等 | VECTOR_LIST | 轴列表 |
| Planes | `output_planes` | PLANE_LIST | 各架平面 |
| Count | `output_count` | INTEGER | 架数量 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 非空列表时为 true |

## 使用步骤

1. 接阵列 / 分布产生的 FRAME_LIST。
2. 用 Origins 做点列下游，或 Axes 做方向场。

## 典型接法

```text
[Linear Array / Grid].Frames ──► [Deconstruct Frames]
[Deconstruct Frames].Origins ──► [Points To Path / Scatter]
```

## 注意事项

- 空列表或非法输入 → Valid false。
