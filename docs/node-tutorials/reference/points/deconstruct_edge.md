# Deconstruct Face Edge

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.points.deconstruct_edge` |
| 分类 | `reference.points` |
| 显示名称 | Deconstruct Face Edge |
| 效果 | `PURE` |

## 作用

拆 `LINE` 边：起终点、中点、单位方向、位移向量、长度。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Edge | `input_edge` | LINE |
| 出 | Start / End / Midpoint | … | POINT |
| 出 | Direction | … | VECTOR（归一化） |
| 出 | Vector | … | VECTOR（全位移） |
| 出 | Length | … | DOUBLE |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[Get Face Edge].Edge ──► [Deconstruct Face Edge].Direction ──► 阵列方向
```
