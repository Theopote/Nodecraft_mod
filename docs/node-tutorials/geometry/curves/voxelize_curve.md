# Voxelize Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.voxelize_curve` |
| 分类 | `geometry.curves` |
| 显示名称 | Voxelize Path |
| 效果 | `PURE` |

## 作用

路径直接体素化为圆柱管段方块坐标。面板 Fill Tube / Cap Ends。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / Radius | … | PATH / DOUBLE |
| 出 | Blocks / Blocks Tree / Geometry / Segment Geometry Tree / Points / Region / Length / Count | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Voxelize Path].Path
[Voxelize Path].Blocks ──► [下游].…
```
