# Points To Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.points_to_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Points To Path |
| 效果 | `PURE` |

## 作用

有序点列 → PATH（2 点为线，3+ 为折线）。面板 Close Path。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Points | `input_points` | POINT_LIST |
| 出 | Path | `output_path` | PATH |
| 出 | Path Point Count | `output_count` | INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Points To Path].Points
[Points To Path].Path ──► [下游].…
```
