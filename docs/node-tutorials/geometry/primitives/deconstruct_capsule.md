# Deconstruct Capsule

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.deconstruct_capsule` |
| 分类 | `geometry.primitives` |
| 显示名称 | Deconstruct Capsule |
| 效果 | `PURE` |

## 作用

从统一 `GEOMETRY` 胶囊拆回轴、半径、中间圆柱与两端半球，以及解析量。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Geometry | `input_geometry` | GEOMETRY（须为胶囊复合） |
| 出 | Start / End / Axis Path / Axis Length / Radius / Total Length | … | … |
| 出 | Cylinder / Start Hemisphere / End Hemisphere | … | 专用几何 |
| 出 | Surface Area / Volume / Region / Bounding Box | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Deconstruct Capsule].Geometry
[Deconstruct Capsule].Start ──► [下游].…
```

## 注意事项

- 仅接受合法胶囊复合几何；普通圆柱会失败。
