# Region Offset In Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.region_offset_plane` |
| 分类 | `geometry.profiles` |
| 显示名称 | Region Offset In Plane |
| 效果 | `PURE` |

## 作用

平面 Region 面内偏移；尽量保留孔拓扑。面板可调 Miter Limit。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Region / Offset | … | PLANAR_REGION / DOUBLE（有符号） |
| 出 | Region / Regions / Profile / Profiles / Plane / Center / Count | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Region Offset In Plane].Region
[Region Offset In Plane].Region ──► [下游].…
```

## 注意事项

- 正偏移外扩、负偏移内缩；过大负偏移可能碎裂为多 Region。
