# Resample Polygon Profile

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.profiles.resample_profile` |
| 分类 | `geometry.profiles` |
| 显示名称 | Resample Polygon Profile |
| 效果 | `PURE` |

## 作用

按周长距离重采样到目标边数。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Profile / Edge Count | … | POLYGON_PROFILE / INTEGER |
| 出 | Profile / Points / Boundary / Plane / Center / Edge Count | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Resample Polygon Profile].Profile
[Resample Polygon Profile].Profile ──► [下游].…
```
