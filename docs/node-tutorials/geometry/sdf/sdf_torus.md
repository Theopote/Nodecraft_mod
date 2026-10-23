# SDF Torus

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_torus` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Torus |
| 效果 | `PURE` |

## 作用

环面 SDF（绕局部 Y）：中心 + 大/小半径。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Center / Major Radius / Minor Radius | … | POINT / DOUBLE / DOUBLE |
| 出 | SDF | `output_sdf` | SDF |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Torus].Center
[SDF Torus].SDF ──► [下游].…
```

## 注意事项

- 须为环面：Major > Minor > 0。
