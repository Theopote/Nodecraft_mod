# Loft Point Lists

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.loft_from_points` |
| 分类 | `geometry.solids` |
| 显示名称 | Loft Point Lists |
| 效果 | `PURE` |

## 作用

两等长有序点列放样；输出路径、轨与 Surface Strip。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Source / Target Points | … | POINT_LIST（数量须相等） |
| 出 | Paths · Rail Paths · Surface Strip · Count · Trees | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Loft Point Lists].Source
[Loft Point Lists].Paths ──► [下游].…
```
