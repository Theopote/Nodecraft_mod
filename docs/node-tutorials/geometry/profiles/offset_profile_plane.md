# Profile Offset In Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.offset_profile_plane` |
| 分类 | `geometry.profiles` |
| 显示名称 | Profile Offset In Plane |
| 效果 | `PURE` |

## 作用

便捷节点：轮廓提升为 Region 后做面内偏移（正外扩 / 负内缩）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Profile / Offset | … | POLYGON_PROFILE / DOUBLE |
| 出 | Region / Regions | … | PLANAR_REGION / PLANAR_REGION_LIST |
| 出 | Profile / Profiles | … | 主区外轮廓便捷口（有孔时不全） |
| 出 | Plane / Center / Count | … | PLANE / POINT / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Profile Offset In Plane].Profile
[Profile Offset In Plane].Region ──► [下游].…
```

## 注意事项

- 完整拓扑（含孔）请用 **Region** 输出，或直接用 [Region Offset In Plane](./region_offset_plane.md)。
