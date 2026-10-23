# Extend Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.extend_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Extend Path |
| 效果 | `PURE` |

## 作用

沿起/终切线线性延长开路径（长度 ≥ 0）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / Start Length / End Length | … | PATH / DOUBLE / DOUBLE |
| 出 | Path | `output_path` | PATH |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Extend Path].Path
[Extend Path].Path ──► [下游].…
```
