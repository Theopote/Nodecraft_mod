# Block Position Input

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.block_position` |
| 分类 | `reference.points` |
| 显示名称 | Block Position Input |
| 效果 | `PURE` |

## 作用

面板或可选 X/Y/Z 口输入整数方块坐标，输出 `BLOCK_POS`。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| X / Y / Z | `input_x` 等 | INTEGER | 可选覆盖；未接用属性 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Block Pos | `output_block_pos` | BLOCK_POS | 方块位置 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 分量是否合法 |

### 属性

X / Y / Z 整数分量（默认 0）。

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[Block Position Input].Block Pos ──► [Block To Point] / [World Read]
```

## 注意事项

- 输出是格点，不是连续 POINT；接几何前先 Block To Point。
