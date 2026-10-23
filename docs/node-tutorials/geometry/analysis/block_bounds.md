# Block Bounds

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.analysis.block_bounds` |
| 分类 | `geometry.analysis` |
| 显示名称 | Block Bounds |
| 效果 | `PURE` |

## 作用

由 Blocks **或** Region（二选一）求连续 AABB 与离散区域尺寸。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Blocks / Region（恰连其一） | BLOCK_LIST / REGION |
| 出 | Bounding Box / Region / Min·Max Block / Size X·Y·Z / Volume / Center | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Block Bounds].Blocks
[Block Bounds].Bounding Box ──► [下游].…
```
