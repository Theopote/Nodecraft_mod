# Scalar Field From SDF

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.scalar_from_sdf` |
| 分类 | `math.fields` |
| 显示名称 | Scalar Field From SDF |
| 效果 | `PURE` |

## 作用

把 SDF 距离包装为标量场。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | SDF | SDF |
| 出 | Field | SCALAR_FIELD |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Scalar Field From SDF].SDF
[Scalar Field From SDF].Field ──► [下游].…
```
