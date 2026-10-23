# Sweep Surface From Points

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.sweep_from_points` |
| 分类 | `geometry.solids` |
| 显示名称 | Sweep Surface From Points |
| 效果 | `PURE` |

## 作用

点列轮廓沿路径扫掠为 SURFACE_STRIP。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Profile Points / Path / Scale / Rotation Values | … | POINT_LIST / PATH / … |
| 出 | Surface Strip · Section Paths · Rails · Trees · Count | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Sweep Surface From Points].Profile Points
[Sweep Surface From Points].Surface Strip ──► [下游].…
```
