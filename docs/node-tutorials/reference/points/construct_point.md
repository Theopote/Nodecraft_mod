# Construct Point

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.construct_point` |
| 分类 | `reference.points` |
| 显示名称 | Construct Point |
| 效果 | `PURE` |

## 作用

由 DOUBLE 的 X/Y/Z 构造几何 `POINT`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | X / Y / Z | `input_x` 等 | DOUBLE |
| 出 | Point | `output_point` | POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[Number Input]×3 ──► [Construct Point] ──► [Sphere].Center
```

## 注意事项

- 分量须有限；NaN/Inf → Valid false。
