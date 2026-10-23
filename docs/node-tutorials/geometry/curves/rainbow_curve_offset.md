# Rainbow Curve Offset

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.rainbow_curve_offset` |
| 分类 | `geometry.curves` |
| 显示名称 | Rainbow Curve Offset |
| 效果 | `PURE` |

## 作用

沿路径帧生成多条平行偏移路径（彩虹带）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / Count / Separation / Spacing / Sample Count / Up Vector | … | … |
| 出 | Paths / Center Path / Offsets / Count / Length | … | PATH_LIST / PATH / DOUBLE_LIST / … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Rainbow Curve Offset].Path
[Rainbow Curve Offset].Paths ──► [下游].…
```
