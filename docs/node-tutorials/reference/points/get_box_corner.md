# Get Box Corner

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.get_box_corner` |
| 分类 | `reference.points` |
| 显示名称 | Get Box Corner |
| 效果 | `PURE` |

## 作用

按索引 0–7 取盒体一角，输出 `POINT`。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Box Geometry | `input_box_geometry` | BOX_GEOMETRY |
| 入 | Corner Index | `input_index` | INTEGER |
| 出 | Corner | `output_corner` | POINT |
| 出 | Found | `output_found` | BOOLEAN |
| 出 | Resolved Index | `output_resolved_index` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

### 属性

Allow Negative Index / Wrap Index（负索引与环绕）。

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Get Box Corner].Box Geometry
[Get Box Corner].Corner ──► [下游].…
```

## 注意事项

- Corner 是 POINT，不是 VECTOR。
