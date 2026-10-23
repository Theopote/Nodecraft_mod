# Deconstruct Octahedron

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_octahedron` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Octahedron |
| 效果 | `PURE` |

## 作用

拆正八面体：中心、Size（到顶点距离）、顶点、解析量、朝向。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Octahedron | `input_octahedron` | OCTAHEDRON_GEOMETRY |
| 出 | Center / Size / Vertices / 面积 / Volume / Region / Bounding Box / Orientation | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Octahedron].Octahedron
[Deconstruct Octahedron].Center ──► [下游].…
```
