# Deconstruct Tetrahedron

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_tetrahedron` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Tetrahedron |
| 效果 | `PURE` |

## 作用

拆正四面体：中心、边长、外接球半径、顶点、面积/体积、朝向矩阵。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Tetrahedron | `input_tetrahedron` | TETRAHEDRON_GEOMETRY |
| 出 | Center / Edge Length / Circumradius / Vertices | … | … |
| 出 | Surface Area / Volume / Region / Bounding Box / Orientation | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Tetrahedron].Tetrahedron
[Deconstruct Tetrahedron].Center ──► [下游].…
```
