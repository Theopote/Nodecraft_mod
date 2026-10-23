# Helix Curve

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.helix` |
| 分类 | `geometry.curves` |
| 显示名称 | Helix Curve |
| 效果 | `PURE` |

## 作用

螺旋：中心、轴、半径、螺距、圈数、每圈分段、起始角。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Axis / Radius / Pitch / Turns / Segments Per Turn / Start Angle | … | … |
| 出 | Path / Points / Length | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Helix Curve].Center
[Helix Curve].Path ──► [下游].…
```
