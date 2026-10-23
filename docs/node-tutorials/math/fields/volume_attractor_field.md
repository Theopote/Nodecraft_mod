# Volume Attractor Field

| 字段 | 值 |
|---|---|
| 节点 ID | `math.fields.volume_attractor_field` |
| 分类 | `math.fields` |
| 显示名称 | Volume Attractor Field |
| 效果 | `PURE` |

## 作用

体积吸引子：向中心或几何/SDF 最近表面拉。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Geometry | GEOMETRY |
| 入 | SDF | SDF |
| 入 | Center | POINT |
| 入 | Strength | DOUBLE |
| 入 | Radius | DOUBLE |
| 入 | Exponent | DOUBLE |
| 入 | SDF Step | DOUBLE |
| 出 | Field | VECTOR_FIELD |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Volume Attractor Field].Geometry
[Volume Attractor Field].Field ──► [下游].…
```
