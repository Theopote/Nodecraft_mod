# Octahedron By Center Size

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.octahedron` |
| 分类 | `geometry.primitives` |
| 显示名称 | Octahedron By Center Size |
| 效果 | `PURE` |

## 作用

中心 + Size（中心到顶点距离）→ 正八面体。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Size / Orientation | … | POINT / DOUBLE / MATRIX3 |
| 出 | Octahedron / Geometry / Center / Size / Vertices | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Octahedron By Center Size].Center
[Octahedron By Center Size].Octahedron ──► [下游].…
```

## 注意事项

- Center / 尺寸须有限且尺寸 > 0。
- Orientation（MATRIX3）可选；未接时用面板欧拉角（度）。
- 同时输出专用类型与统一 `GEOMETRY`。
