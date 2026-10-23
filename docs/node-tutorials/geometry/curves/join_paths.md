# Join Paths

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.join_paths` |
| 分类 | `geometry.curves` |
| 显示名称 | Join Paths |
| 效果 | `PURE` |

## 作用

当 A 终点与 B 起点在容差内相遇时拼接。**不**桥接、不自动反向。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path A / Path B / Tolerance | … | PATH / PATH / DOUBLE |
| 出 | Path / Count | … | PATH / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Join Paths].Path A
[Join Paths].Path ──► [下游].…
```
