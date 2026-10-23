# Export CSV / JSON

| 字段 | 值 |
|---|---|
| 节点 ID | `output.export.export_data` |
| 分类 | `output.export` |
| 显示名称 | Export CSV / JSON |
| 效果 | `FILE_IO` |

## 作用

把列表/坐标数据导出为 CSV 或 JSON（Format 字符串）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Trigger / Data / Blocks / Path / Format | EXEC / LIST / BLOCK_LIST / STRING |
| 出 | Success / Path / Count / Format / Error | … |

## 使用步骤

1. 按端点表连接输入。
2. 查看输出并接到下游。

## 典型接法

```text
[上游].… ──► [Export CSV / JSON].Trigger
[Export CSV / JSON].Success ──► [下游].…
```
