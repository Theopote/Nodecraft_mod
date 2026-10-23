# Deconstruct Polygon Profile

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.deconstruct_profile` |
| 分类 | `geometry.profiles` |
| 显示名称 | Deconstruct Polygon Profile |
| 效果 | `PURE` |

## 作用

拆轮廓：点列、边界、平面、中心、边数、周长、面积、法线。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Profile | `input_profile` | POLYGON_PROFILE |
| 出 | Points / Boundary / Plane / Center / Edges | … | … |
| 出 | Perimeter / Area / Normal | … | DOUBLE / DOUBLE / VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Polygon Profile].Profile
[Deconstruct Polygon Profile].Points ──► [下游].…
```
