# Bezier

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.bezier` |
| 分类 | `geometry.curves` |
| 显示名称 | Bezier |
| 效果 | `PURE` |

## 作用

控制点列表采样贝塞尔曲线（至少 3 点）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Control Points / Samples | … | POINT_LIST / INTEGER |
| 出 | Path / Points / Control Path / Control Count / Length | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Bezier].Control Points
[Bezier].Path ──► [下游].…
```
