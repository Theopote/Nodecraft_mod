# Deconstruct Dodecahedron

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_dodecahedron` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Dodecahedron |
| 效果 | `PURE` |

## 作用

拆正十二面体参数与顶点。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Dodecahedron | `input_dodecahedron` | DODECAHEDRON_GEOMETRY |
| 出 | Center / Edge Length / Circumradius / Vertices / … / Orientation | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Dodecahedron].Dodecahedron
[Deconstruct Dodecahedron].Center ──► [下游].…
```
