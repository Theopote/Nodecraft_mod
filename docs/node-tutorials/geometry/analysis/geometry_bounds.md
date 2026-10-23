# Geometry Bounds

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.analysis.geometry_bounds` |
| 分类 | `geometry.analysis` |
| 显示名称 | Geometry Bounds |
| 效果 | `PURE` |

## 作用

任意支持的 GEOMETRY → 连续轴对齐包围盒与尺寸。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry | GEOMETRY |
| 出 | Bounding Box / Min / Max / Center / Size X·Y·Z / Volume | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Geometry Bounds].Geometry
[Geometry Bounds].Bounding Box ──► [下游].…
```
