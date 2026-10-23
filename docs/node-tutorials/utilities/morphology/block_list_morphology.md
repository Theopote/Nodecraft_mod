# Block Morphology

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.morphology.block_list_morphology` |
| 分类 | `utilities.morphology` |
| 显示名称 | Block Morphology |
| 效果 | `PURE` |

## 作用

对方块集合做膨胀或腐蚀。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Blocks | BLOCK_LIST |
| 入 | Iterations | INTEGER |
| 出 | Blocks | BLOCK_LIST |
| 出 | Input Count | INTEGER |
| 出 | Output Count | INTEGER |
| 出 | Delta Count | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Block Morphology].Blocks
[Block Morphology].Blocks ──► [下游].…
```
