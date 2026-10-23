# Intersection

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.intersection` |
| 分类 | `geometry.boolean` |
| 显示名称 | Intersection |
| 效果 | `PURE` |

## 作用

保留两侧重叠的体素（延迟交集）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Left Geometry / Right Geometry | `input_left` / `input_right` | GEOMETRY |
| 出 | Geometry | `output_geometry` | GEOMETRY |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Intersection].Left Geometry
[Intersection].Geometry ──► [下游].…
```
