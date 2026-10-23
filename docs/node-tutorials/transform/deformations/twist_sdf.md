# Twist SDF

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.deformations.twist_sdf` |
| 分类 | `transform.deformations` |
| 显示名称 | Twist SDF |
| 效果 | `PURE` |

## 作用

对 SDF 做轴向扭转**域变形**；可显式 Bounds Min/Max。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | SDF / Axis Origin·Direction / Angle Degrees / Twist Length / Bounds Min·Max | … |
| 出 | SDF / Bounds Min·Max | SDF / POINT |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Twist SDF].SDF
[Twist SDF].SDF ──► [下游].…
```
