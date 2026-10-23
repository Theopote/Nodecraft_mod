# Deconstruct Torus

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_torus` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Torus |
| 效果 | `PURE` |

## 作用

拆环面中心、单位轴、大小半径、面积/体积、包围。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Torus | `input_torus` | TORUS_GEOMETRY |
| 出 | Center / Axis / Major / Minor Radius | … | POINT / VECTOR / DOUBLE / DOUBLE |
| 出 | Surface Area / Volume / Region / Bounding Box | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Torus].Torus
[Deconstruct Torus].Center ──► [下游].…
```
