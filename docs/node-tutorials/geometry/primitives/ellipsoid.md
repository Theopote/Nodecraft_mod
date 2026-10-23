# Ellipsoid By Center Radii

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.ellipsoid` |
| 分类 | `geometry.primitives` |
| 显示名称 | Ellipsoid By Center Radii |
| 效果 | `PURE` |

## 作用

中心 + X/Y/Z 半径构造椭球。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Radius X/Y/Z | … | POINT / DOUBLE×3 |
| 出 | Ellipsoid / Geometry | … | ELLIPSOID_GEOMETRY / GEOMETRY |
| 出 | Center / Radii | … | POINT / VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Ellipsoid By Center Radii].Center
[Ellipsoid By Center Radii].Ellipsoid ──► [下游].…
```
