# Deconstruct Surface Strip

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.deconstruct_surface_strip` |
| 分类 | `geometry.solids` |
| 显示名称 | Deconstruct Surface Strip |
| 效果 | `PURE` |

## 作用

拆条带：截面路径、展平点、轨段与统计。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Surface Strip | … | SURFACE_STRIP |
| 出 | Section Paths · All Points · Rail Segments · Trees | … | … |
| 出 | Section Count / Points Per Section / All Closed | … | INTEGER / INTEGER / BOOLEAN |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Surface Strip].Surface Strip
[Deconstruct Surface Strip].Section Paths ──► [下游].…
```
