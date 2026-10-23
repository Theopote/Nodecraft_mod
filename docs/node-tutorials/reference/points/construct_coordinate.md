# Construct Block Position

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.construct_coordinate` |
| 分类 | `reference.points` |
| 显示名称 | Construct Block Position |
| 效果 | `PURE` |

## 作用

由精确 INTEGER 的 X/Y/Z 构造 `BLOCK_POS`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | X / Y / Z | `input_x` 等 | INTEGER |
| 出 | Block Pos | `output_block_pos` | BLOCK_POS |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Construct Block Position].X
[Construct Block Position].Block Pos ──► [下游].…
```

## 注意事项

- 分量必须是精确 INTEGER，否则 Valid false。
