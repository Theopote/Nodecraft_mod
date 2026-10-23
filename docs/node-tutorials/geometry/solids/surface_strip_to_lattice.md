# Surface Strip To Lattice

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.surface_strip_to_lattice` |
| 分类 | `geometry.solids` |
| 显示名称 | Surface Strip To Lattice |
| 效果 | `PURE` |

## 作用

条带近似为圆柱格子线框（截面边+轨），**非**实心壳。可接树输入。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Surface Strip / Surface Strip Tree | … | SURFACE_STRIP / DATA_TREE |
| 出 | Lattice Geometry / Lattice Geometry Tree | … | GEOMETRY / DATA_TREE |
| 出 | Region / Count 等 | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Surface Strip To Lattice].Surface Strip
[Surface Strip To Lattice].Lattice Geometry ──► [下游].…
```
