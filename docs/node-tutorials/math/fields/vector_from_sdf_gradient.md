# Vector Field From SDF Gradient

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.vector_from_sdf_gradient` |
| 分类 | `math.fields` |
| 显示名称 | Vector Field From SDF Gradient |
| 效果 | `PURE` |

## 作用

SDF 中心差分梯度向量场（归一化方向）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | SDF | SDF |
| 入 | Step | DOUBLE |
| 出 | Field | VECTOR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Vector Field From SDF Gradient].SDF
[Vector Field From SDF Gradient].Field ──► [下游].…
```
