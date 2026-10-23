# Interpolate Spline

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.interpolate_spline` |
| 分类 | `geometry.curves` |
| 显示名称 | Interpolate Spline |
| 效果 | `PURE` |

## 作用

Catmull-Rom 插值样条，**穿过**全部输入点。面板 Closed；Alpha：0 均匀 / 0.5 向心 / 1 弦长。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points / Resolution·Segment / Alpha | … | POINT_LIST / INTEGER / DOUBLE |
| 出 | Path / Points / Control Path / Control Count / Length | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Interpolate Spline].Points
[Interpolate Spline].Path ──► [下游].…
```
