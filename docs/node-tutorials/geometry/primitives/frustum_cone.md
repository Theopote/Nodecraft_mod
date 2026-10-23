# Frustum By Two Centers Radii

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.primitives.frustum_cone` |
| 分类 | `geometry.primitives` |
| 显示名称 | Frustum By Two Centers Radii |
| 效果 | `PURE` |

## 作用

两平行圆面中心 + 各自半径 → 圆台。顶半径为 0 时退化为尖锥。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Base Center / Top Center | … | POINT |
| 入 | Base Radius / Top Radius | … | DOUBLE |
| 出 | Frustum / Geometry | … | FRUSTUM_CONE_GEOMETRY / GEOMETRY |
| 出 | Axis Path / Axis Vector / Height | … | PATH / VECTOR / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Frustum By Two Centers Radii].Base Center
[Frustum By Two Centers Radii].Frustum ──► [下游].…
```

## 注意事项

- 两中心不可重合；半径须有限且 ≥ 0。
