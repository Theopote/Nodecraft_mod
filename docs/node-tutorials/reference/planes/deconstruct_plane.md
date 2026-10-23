# Deconstruct Plane

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.planes.deconstruct_plane` |
| 分类 | `reference.planes` |
| 显示名称 | Deconstruct Plane |
| 效果 | `PURE` |

## 作用

把 PLANE 拆成 **Origin**（POINT）与 **Normal**（VECTOR），便于单独用原点或法线做下游计算。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Plane | `input_plane` | PLANE | 待拆解平面 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Origin | `output_origin` | POINT | 平面原点 |
| Normal | `output_normal` | VECTOR | 法线 |
| Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING | 是否为可用 canonical 平面 |

## 使用步骤

1. 接任意上游 Plane。
2. 用 Origin 当放置点，用 Normal 当朝向 / 偏移方向。

## 典型接法

```text
[World Plane].Plane ──► [Deconstruct Plane]
[Deconstruct Plane].Origin ──► [Construct Frame].Origin
[Deconstruct Plane].Normal ──► [Offset / 朝向逻辑]
```

## 注意事项

- 非 canonical 平面 → Valid false。
