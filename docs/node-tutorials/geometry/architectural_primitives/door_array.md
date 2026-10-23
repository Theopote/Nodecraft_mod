# Door Array

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.door_array` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Door Array |
| 效果 | `PURE` |

## 作用

面中心对齐的**门洞切割体**阵列；参数与 Window Array 同类，尺寸用 Door Width / Height。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Columns / Rows / Door Width·Height / Margin / Depth | BOX_FACE / … |
| 入 | Layout Mode / Gaps / Bay Width | STRING / DOUBLE |
| 出 | Openings / Frames / Centers / Indices / Count | GEOMETRY / … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Door Array].Face
[Door Array].Openings ──► [下游].…
```

## 注意事项

- 通常 Rows=1、门贴底边；Margin 控制距面边距离。
