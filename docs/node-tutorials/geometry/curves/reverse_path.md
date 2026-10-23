# Reverse Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.reverse_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Reverse Path |
| 效果 | `PURE` |

## 作用

反转路径方向。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path | `input_path` | PATH |
| 出 | Path | `output_path` | PATH |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Reverse Path].Path
[Reverse Path].Path ──► [下游].…
```
