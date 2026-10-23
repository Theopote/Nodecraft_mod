# Relax Point List

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.deformations.relax_points` |
| 分类 | `transform.deformations` |
| 显示名称 | Relax Point List |
| 效果 | `PURE` |

## 作用

k 近邻 Laplacian 式平滑；Iterations / Blend 控制次数与混合。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points / K / Iterations / Blend | POINT_LIST / INTEGER / DOUBLE |
| 出 | Points / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Relax Point List].Points
[Relax Point List].Points ──► [下游].…
```
