# Box Face Boundary Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.face_boundary_curve` |
| 分类 | `geometry.curves` |
| 显示名称 | Box Face Boundary Path |
| 效果 | `PURE` |

## 作用

盒体面 → 闭合边界 PATH（常接 Wall Along Path 等）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Face | `input_face` | BOX_FACE |
| 出 | Path / Points / Corner Indices / Name / Index | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Box Face Boundary Path].Face
[Box Face Boundary Path].Path ──► [下游].…
```
