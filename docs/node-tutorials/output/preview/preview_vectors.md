# Preview Vectors

| 字段 | 值 |
|---|---|
| 节点 ID | `output.preview.preview_vectors` |
| 分类 | `output.preview` |
| 显示名称 | Preview Vectors |
| 效果 | `PREVIEW_WRITE` |

## 作用

从 Start Point 预览向量方向。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Vector / Start Point | VECTOR / BLOCK_POS |
| 出 | Success / Preview ID | … |

## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Preview Vectors].Vector
[Preview Vectors].Success ──► [下游].…
```
