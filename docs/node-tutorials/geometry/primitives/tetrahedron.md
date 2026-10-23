# Tetrahedron By Center Edge

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.tetrahedron` |
| 分类 | `geometry.primitives` |
| 显示名称 | Tetrahedron By Center Edge |
| 效果 | `PURE` |

## 作用

中心 + 边长 + 可选朝向 → 正四面体。另输出 Circumradius。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Edge Length / Orientation | … | POINT / DOUBLE / MATRIX3 |
| 出 | Tetrahedron / Geometry / Center / Edge Length / Vertices | … | … |
| 出 | Circumradius | `output_circumradius` | DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Tetrahedron By Center Edge].Center
[Tetrahedron By Center Edge].Tetrahedron ──► [下游].…
```

## 注意事项

- Center / 尺寸须有限且尺寸 > 0。
- Orientation（MATRIX3）可选；未接时用面板欧拉角（度）。
- 同时输出专用类型与统一 `GEOMETRY`。
