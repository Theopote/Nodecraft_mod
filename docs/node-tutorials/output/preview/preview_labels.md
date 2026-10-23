# Preview Labels

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_labels` |
| 分类 | `output.preview` |
| 显示名称 | Preview Labels |
| 效果 | `PREVIEW_WRITE` |

## 作用

在参考位置显示文本标签。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Position / Text | BLOCK_POS / STRING |
| 出 | Success / Preview ID | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Labels].Position
[Preview Labels].Success ──► [下游].…
```
