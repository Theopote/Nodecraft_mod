# Face Center Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.frames.frame_from_face` |
| 分类 | `reference.frames` |
| 显示名称 | Face Center Frame |
| 效果 | `PURE` |

## 作用

在盒体面中心建立正交 FRAME，并输出面中心点。适合贴面放置柱、窗、装饰。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Face | `input_face` | BOX_FACE | 源盒体面 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frame | `output_frame` | FRAME | 面中心定向架 |
| Center | `output_center` | POINT | 面中心（架原点） |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 面是否可用 |

## 使用步骤

1. 用 Get Box Face 取出面。
2. 接到本节点得到 Frame / Center。
3. 用 Frame 放置阵列或单体构件。

## 典型接法

```text
[Get Box Face].Face ──► [Face Center Frame].Face
[Face Center Frame].Frame ──► [Window Array / Column].Frame
```

## 注意事项

- 非法 BOX_FACE → Valid false。
