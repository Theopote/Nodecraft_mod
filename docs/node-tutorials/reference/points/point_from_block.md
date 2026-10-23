# Block To Point

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.point_from_block` |
| 分类 | `reference.points` |
| 显示名称 | Block To Point |
| 效果 | `PURE` |

## 作用

把 `BLOCK_POS` **显式**转为连续 `POINT`。默认使用**方块中心**（`useBlockCenter = true`）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Coordinate | `input_coordinate` | BLOCK_POS |
| 出 | Point | `output_point` | POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Block To Point].Coordinate
[Block To Point].Point ──► [下游].…
```

## 注意事项

- 关闭方块中心时落在格点角（以实现为准）；几何 Center 口通常用中心。
- 不要把 BLOCK_POS 直接当 POINT 接，必须经本节点。
