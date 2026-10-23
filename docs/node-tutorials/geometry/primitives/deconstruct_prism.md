# Deconstruct Prism

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_prism` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Prism |
| 效果 | `PURE` |

## 作用

拆棱柱：底/顶多边形点列、挤出向量、侧带（Surface Strip）、包围。棱柱通常由 profiles/solids 挤出产生。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Prism | `input_prism` | PRISM_GEOMETRY |
| 出 | Base / Top Points | … | POINT_LIST |
| 出 | Extrusion Vector / Height / Side Count | … | VECTOR / DOUBLE / INTEGER |
| 出 | Surface Strip | `output_surface_strip` | SURFACE_STRIP |
| 出 | Region / Bounding Box | … | REGION / BOUNDING_BOX |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Prism].Prism
[Deconstruct Prism].Base ──► [下游].…
```
