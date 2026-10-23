# Scatter In Volume

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.surface_volume_distribution.scatter_volume` |
| 分类 | `pattern.surface_volume_distribution` |
| 显示名称 | Scatter In Volume |
| 效果 | `PURE` |

## 作用

在支持的原语**体积内**散射（随机或近似蓝噪声）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry / Target Count / Seed / Min Distance | GEOMETRY / … |
| 出 | Points / Count | POINT_LIST / INTEGER |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Scatter In Volume].Geometry
[Scatter In Volume].Points ──► [下游].…
```
