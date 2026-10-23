# Railing

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.railing` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Railing |
| 效果 | `PURE` |

## 作用

沿 PATH（线/折线/曲线）生成栏杆：立柱 + 多层横杆。`Join`：`miter` / `bevel` / `butt`；`Vertical Mode` 决定高度轴。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Path / Post Count / Height / Post Radius / Rail Count / Rail Radius | PATH / … |
| 入 | Offset / Join / Vertical Mode | DOUBLE / STRING |
| 出 | Geometry / Count | GEOMETRY / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Railing].Path
[Railing].Geometry ──► [下游].…
```
