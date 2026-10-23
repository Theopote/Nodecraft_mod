# Closest Point On Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.closest_point_on_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Closest Point On Path |
| 效果 | `PURE` |

## 作用

查询点到路径最近点，并输出距离、归一化参数与弧长。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / Point | … | PATH / POINT |
| 出 | Closest Point / Distance / Parameter / Arc Length | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Closest Point On Path].Path
[Closest Point On Path].Closest Point ──► [下游].…
```
