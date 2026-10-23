# Annulus On Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.annulus_profile` |
| 分类 | `geometry.profiles` |
| 显示名称 | Annulus On Plane |
| 效果 | `PURE` |

## 作用

圆环带：外环 + 内孔。同时输出内外轮廓与 `PLANAR_REGION`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Inner Radius / Outer Radius / Segments | … | … |
| 入 | Plane / Start Direction | … | PLANE / VECTOR |
| 出 | Outer / Inner Points · Profiles · Boundaries | … | POINT_LIST / POLYGON_PROFILE / PATH |
| 出 | Region | `output_region` | PLANAR_REGION（外环 + 一孔） |
| 出 | Area | `output_area` | DOUBLE，π(R²−r²) |
| 出 | Plane / Center | … | PLANE / POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Annulus On Plane].Center
[Annulus On Plane].Outer ──► [下游].…
```

## 注意事项

- Outer > Inner > 0。有孔场景优先用 Region 下游，勿只接 Outer Profile。
