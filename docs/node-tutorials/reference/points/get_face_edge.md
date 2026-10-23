# Get Face Edge

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.get_face_edge` |
| 分类 | `reference.points` |
| 显示名称 | Get Face Edge |
| 效果 | `PURE` |

## 作用

按面绕序索引 0–3 取面的一条边，输出 `LINE`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Face | `input_face` | BOX_FACE |
| 入 | Edge Index | `input_index` | INTEGER |
| 出 | Edge | `output_edge` | LINE |
| 出 | Found / Resolved Index / Valid / Error | … | … |

### 属性

Allow Negative Index / Wrap Index。

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Get Face Edge].Face
[Get Face Edge].Edge ──► [下游].…
```
