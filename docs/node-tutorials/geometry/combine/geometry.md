# Combine Geometry

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.combine.geometry` |
| 分类 | `geometry.combine` |
| 显示名称 | Combine Geometry |
| 效果 | `PURE` |

## 作用

把多路 GEOMETRY 合成一个复合体。面板可调输入个数（2–16）：`Geometry 1` … `Geometry N`。体素化时为方块**集合并**。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Geometry 1…N | `input_geometry_0`… | GEOMETRY |
| 出 | Geometry / Count | `output_geometry` / `output_count` | GEOMETRY / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Combine Geometry].Geometry 1…N
[Combine Geometry].Geometry ──► [下游].…
```

## 注意事项

- 不是 SDF 光滑并，也不是 BRep Union。
