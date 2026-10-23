# Deconstruct Block Position

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.deconstruct_block_position` |
| 分类 | `reference.points` |
| 显示名称 | Deconstruct Block Position |
| 效果 | `PURE` |

## 作用

把 `BLOCK_POS` 拆成整数 X/Y/Z。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Block Pos | （输入口） | BLOCK_POS |
| 出 | X / Y / Z | `output_x` 等 | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[某查询].Block Pos ──► [Deconstruct Block Position] ──► 算术 / 比较
```
