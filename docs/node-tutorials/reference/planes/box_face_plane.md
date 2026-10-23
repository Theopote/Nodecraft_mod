# Box Face To Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.planes.box_face_plane` |
| 分类 | `reference.planes` |
| 显示名称 | Box Face To Plane |
| 效果 | `PURE` |

## 作用

把盒体的一个面（BOX_FACE）转成其支撑平面：原点取面中心，法线取面法线。常接在 Get Box Face 之后，做贴面轮廓、开窗切割参照。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Face | `input_face` | BOX_FACE | 盒体面 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Plane | `output_plane` | PLANE | 包含该面的平面 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 面是否合法 |

## 使用步骤

1. 用 Get Box Face（`reference.points`）取出目标面。
2. 接到本节点得到 Plane。
3. （可选）再 [Offset Plane](./offset_plane.md) 外扩/内缩。

## 典型接法

```text
[Box].Geometry ──► [Get Box Face].Box
               ──► [Box Face To Plane].Face
               ──► [Offset Plane] / [Wall / Window 阵列参照]
```

## 注意事项

- Face 必须通过 BOX_FACE 校验；非法面 Valid false。
