# SDF Sample Point

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_sample_point` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Sample Point |
| 效果 | `PURE` |

## 作用

单点采样：有符号距离 + 是否体内（distance ≤ 0）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | SDF / Point | … | SDF / POINT |
| 出 | Distance / Inside | … | DOUBLE / BOOLEAN |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Sample Point].SDF
[SDF Sample Point].Distance ──► [下游].…
```
