# Vector Between Points

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.vector_between_points` |
| 分类 | `reference.points` |
| 显示名称 | Vector Between Points |
| 效果 | `PURE` |

## 作用

位移向量 **To − From**，并输出 Length。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | From / To | `input_from` / `input_to` | POINT |
| 出 | Vector | `output_vector` | VECTOR |
| 出 | Length | `output_length` | DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[From]+[To] ──► [Vector Between Points].Vector ──► [Normalize / Translate]
```
