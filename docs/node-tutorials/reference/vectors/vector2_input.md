# 2D Vector Input

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.vector2_input` |
| 分类 | `reference.vectors` |
| 显示名称 | 2D Vector Input |
| 效果 | `PURE` |

## 作用

输入 2D（X/Y），输出 `VECTOR(x, y, 0)`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 出 | Vector | `output_vector` | VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [2D Vector Input].In
[2D Vector Input].Vector ──► [下游].…
```

## 注意事项

- Z 固定为 0；适合平面内方向。
