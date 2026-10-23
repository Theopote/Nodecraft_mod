# Panel

| 字段 | 值 |
|---|---|
| 节点 ID | `output.debug.data_inspector` |
| 分类 | `output.debug` |
| 显示名称 | Panel |
| 效果 | `PURE` |

## 作用

把输入值渲染为有界文本供检查。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Data / Use Formatting / Max Length | ANY / BOOLEAN / INTEGER |
| 出 | Text / Text Length / Data Type | STRING / INTEGER / STRING |

## 使用步骤

1. 按端点表连接输入。
2. 查看输出并接到下游。

## 典型接法

```text
[上游].… ──► [Panel].Data
[Panel].Text ──► [下游].…
```
