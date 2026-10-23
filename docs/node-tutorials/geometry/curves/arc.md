# Arc

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.arc` |
| 分类 | `geometry.curves` |
| 显示名称 | Arc |
| 效果 | `PURE` |

## 作用

圆心 + 平面（或法线）+ 半径 + 起终角（度）采样圆弧。扫掠=end−start，**可超过 ±360°**。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Plane / Normal / Radius / Start·End Angle / Samples | … | … |
| 出 | Path / Points / Length / Sweep Degrees | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Arc].Center
[Arc].Path ──► [下游].…
```
