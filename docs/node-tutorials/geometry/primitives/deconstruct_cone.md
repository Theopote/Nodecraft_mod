# Deconstruct Cone

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_cone` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Cone |
| 效果 | `PURE` |

## 作用

拆圆锥底心、顶点、轴与解析量。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Cone | `input_cone` | CONE_GEOMETRY |
| 出 | Base Center / Apex / Axis Path / Axis Vector | … | … |
| 出 | Height / Base Radius / 面积 / Volume | … | DOUBLE |
| 出 | Region / Bounding Box | … | REGION / BOUNDING_BOX |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Cone].Cone
[Deconstruct Cone].Base Center ──► [下游].…
```
