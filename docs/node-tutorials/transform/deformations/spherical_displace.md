# Spherical Displace

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.deformations.spherical_displace` |
| 分类 | `transform.deformations` |
| 显示名称 | Spherical Displace |
| 效果 | `PURE` |

## 作用

绕 Center 径向位移，带球面距离衰减（Falloff Power）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points / Center / Strength / Radius / Falloff Power | … |
| 出 | Points / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Spherical Displace].Points
[Spherical Displace].Points ──► [下游].…
```
