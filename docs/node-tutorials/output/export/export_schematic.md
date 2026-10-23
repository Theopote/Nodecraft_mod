# Export Schematic

| 字段 | 值 |
|---|---|
| 节点 ID | `output.export.export_schematic` |
| 分类 | `output.export` |
| 显示名称 | Export Schematic |
| 效果 | `FILE_IO` |

## 作用

导出为 NodeCraft NBT 结构文件。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger / Blocks / Block Type / Block Placements / Path / Name / Author | EXEC / … |
| 出 | Success / Path / Block Count / Format / Format Version / Error | … |

## 使用步骤

1. 按端点表连接输入。
2. 查看输出并接到下游。

## 典型接法

```text
[上游].… ──► [Export Schematic].Trigger
[Export Schematic].Success ──► [下游].…
```
