# Profile To Region

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.profile_to_region` |
| 分类 | `geometry.profiles` |
| 显示名称 | Profile To Region |
| 效果 | `PURE` |

## 作用

多边形轮廓提升为无孔 `PLANAR_REGION`（外环即该轮廓）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Profile | `input_profile` | POLYGON_PROFILE |
| 出 | Region / Plane | … | PLANAR_REGION / PLANE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[Circle On Plane].Profile ──► [Profile To Region] ──► [Region Boolean 2D]
```
