# Vector Input

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.vector` |
| 分类 | `reference.vectors` |
| 显示名称 | Vector Input |
| 效果 | `PURE` |

## 作用

面板或可选 X/Y/Z 口输入 3D 向量。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | X / Y / Z | `input_x` 等 | DOUBLE（可选覆盖） |
| 出 | Vector | `output_vector` | VECTOR |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Vector Input].X
[Vector Input].Vector ──► [下游].…
```

## 注意事项

- 分量须有限；连接口非法则 Valid false。
