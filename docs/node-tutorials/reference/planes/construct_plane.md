# Construct Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.planes.construct_plane` |
| 分类 | `reference.planes` |
| 显示名称 | Construct Plane |
| 效果 | `PURE` |

## 作用

由平面上一点 **Origin** 与 **Normal** 向量构造 PLANE。法线会被规范化为规范平面。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Origin | `input_origin` | POINT | 平面上一点 |
| Normal | `input_normal` | VECTOR | 法线（非零） |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Plane | `output_plane` | PLANE | 结果平面 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 预检 |

## 使用步骤

1. 接 Origin（点）与 Normal（向量，如 Cross Product 结果）。
2. 检查 Valid；把 Plane 接到下游。

## 典型接法

```text
[Construct Point].Point     ──► [Construct Plane].Origin
[Normalize Vector].Vector   ──► [Construct Plane].Normal
[Construct Plane].Plane     ──► [Mirror Geometry About Plane].Plane
```

## 注意事项

- 零法线 / 非有限点 → Valid false。
- 需要三点定面时用 [Construct Plane From Points](./plane_from_points.md)。
