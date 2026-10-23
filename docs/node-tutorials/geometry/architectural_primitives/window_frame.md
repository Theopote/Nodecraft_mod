# Window Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.window_frame` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Window Frame |
| 效果 | `PURE` |

## 作用

原点居中的空心窗框实体（局部 X/Y/Z），常与 Window Array 的 Frames 做放置。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Frame Width / Frame Height / Frame Thickness / Depth | DOUBLE |
| 出 | Geometry | GEOMETRY |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Window Frame].Frame Width
[Window Frame].Geometry ──► [下游].…
```
