# Noise Displace Point List

| 字段 | 值 |
|---|---|
| 节点 ID | `transform.deformations.noise_displace` |
| 分类 | `transform.deformations` |
| 显示名称 | Noise Displace Point List |
| 效果 | `PURE` |

## 作用

确定性伪噪声位移点列（Amplitude / Frequency / Seed / Offset）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Points / Amplitude / Frequency / Seed / Offset | … |
| 出 | Points / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Noise Displace Point List].Points
[Noise Displace Point List].Points ──► [下游].…
```
