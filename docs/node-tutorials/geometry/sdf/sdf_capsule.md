# SDF Capsule

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_capsule` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Capsule |
| 效果 | `PURE` |

## 作用

线段两端 + 半径 → 胶囊 SDF。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Start / End / Radius | … | POINT / POINT / DOUBLE |
| 出 | SDF | `output_sdf` | SDF |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Capsule].Start
[SDF Capsule].SDF ──► [下游].…
```

## 注意事项

- 轴长与半径均须 > 0。
