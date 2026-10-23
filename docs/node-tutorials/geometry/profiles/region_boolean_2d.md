# Region Boolean 2D

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.region_boolean_2d` |
| 分类 | `geometry.profiles` |
| 显示名称 | Region Boolean 2D |
| 效果 | `PURE` |

## 作用

两共面 `PLANAR_REGION` 做 2D 布尔（支持孔）；操作在面板属性（如 UNION / DIFFERENCE）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Region A / B | `input_region_a` / `input_region_b` | PLANAR_REGION |
| 出 | Region / Regions | … | 主区（面积最大，可含孔）/ 全部 |
| 出 | Profile / Profiles | … | 外轮廓便捷口（有孔时不全） |
| 出 | Plane / Center / Count | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Region Boolean 2D].Region A
[Region Boolean 2D].Region ──► [下游].…
```

## 注意事项

- 须共面。完整拓扑用 Region；仅要外轮廓才用 Profile。
