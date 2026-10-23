# Prism By Points

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.extrude_profile_from_points` |
| 分类 | `geometry.solids` |
| 显示名称 | Prism By Points |
| 效果 | `PURE` |

## 作用

底面有序多边形点列 + 挤出向量 → 棱柱实体。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Base Points / Extrusion Vector | … | POINT_LIST / VECTOR |
| 出 | Prism / Geometry / Surface Strip | … | PRISM_GEOMETRY / GEOMETRY / SURFACE_STRIP |
| 出 | Base / Top Points · Height · Side Count | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Prism By Points].Base Points
[Prism By Points].Prism ──► [下游].…
```

## 注意事项

- 底面至少 3 点；向量非零。
