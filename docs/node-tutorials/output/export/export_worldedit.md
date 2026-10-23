# Export WorldEdit

| 字段 | 值 |
|---|---|
| 节点 ID | `output.export.export_worldedit` |
| 分类 | `output.export` |
| 显示名称 | Export WorldEdit |
| 效果 | `FILE_IO` |

## 作用

导出 Sponge schematic（WorldEdit）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger / Blocks / Block Type / Block Placements / Path / Name / Author | … |
| 出 | Success / Path / Block Count / Format / Format Version / Error | … |

## 使用步骤

1. 按端点表连接输入。
2. 查看输出并接到下游。

## 典型接法

```text
[上游].… ──► [Export WorldEdit].Trigger
[Export WorldEdit].Success ──► [下游].…
```
