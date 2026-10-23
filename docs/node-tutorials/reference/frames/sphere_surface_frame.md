# Sphere Surface Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.frames.sphere_surface_frame` |
| 分类 | `reference.frames` |
| 显示名称 | Sphere Surface Frame |
| 效果 | `PURE` |

## 作用

把参考点投影到球面，在投影点建立**切向局部架**：原点为表面点，法线为外向球法线，并可用 **X Hint** 约束切向 X。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Sphere | `input_sphere` | SPHERE | 目标球 |
| Point | `input_point` | POINT | 近表面或表面上的参考点 |
| X Hint | `input_x_hint` | VECTOR | 可选切向 X 提示 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frame | `output_frame` | FRAME | 球面切向架 |
| Surface Point | `output_origin` | POINT | 投影表面点 |
| Normal | `output_normal` | VECTOR | 外向法线 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 是否成功 |

## 使用步骤

1. 接 Sphere 几何与参考 Point。
2. （可选）接 X Hint。
3. 用 Frame / Surface Point 在球面上放置装饰。

## 典型接法

```text
[Sphere].Sphere + [某点].Point ──► [Sphere Surface Frame]
[Sphere Surface Frame].Frame ──► [贴面放置]
```

## 注意事项

- 点与球心重合等退化情况会失败，看 Valid。
