# Get Box Face

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.get_box_face` |
| 分类 | `reference.points` |
| 显示名称 | Get Box Face |
| 效果 | `PURE` |

## 作用

按语义名（top/bottom/left/right/front/back）或索引 0–5 取盒体面。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Box Geometry | `input_box_geometry` | BOX_GEOMETRY |
| 入 | Face Name | `input_face_name` | STRING（可选） |
| 入 | Face Index | `input_index` | INTEGER（可选） |
| 出 | Face | `output_face` | BOX_FACE |
| 出 | Found / Name / Resolved Index | … | BOOLEAN / STRING / INTEGER |
| 出 | Valid / Error | … | BOOLEAN / STRING |

### 属性

Allow Negative Index、Wrap Index、Default Face Name。

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[Box].Geometry ──► [Get Box Face].Face ──► [Face Center Frame] / [Box Face To Plane]
```
