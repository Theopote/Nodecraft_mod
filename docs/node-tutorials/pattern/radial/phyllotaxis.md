# Phyllotaxis

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.radial.phyllotaxis` |
| 分类 | `pattern.radial` |
| 显示名称 | Phyllotaxis |
| 效果 | `PURE` |

## 作用

黄金角叶序锚点（默认可调 Angle Step）+ 切向 / Frame。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Origin / Count / Radius Scale / Angle Step / Start Angle / Height Step / Radial Exponent | … |
| 出 | Points / Tangents / Frames / Count | … |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Phyllotaxis].Origin
[Phyllotaxis].Points ──► [下游].…
```
