# Trim Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.trim_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Trim Path |
| 效果 | `PURE` |

## 作用

截取归一化参数 [Start, End]⊆[0,1] 子路径。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / Start / End | … | PATH / DOUBLE / DOUBLE |
| 出 | Path | `output_path` | PATH |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Trim Path].Path
[Trim Path].Path ──► [下游].…
```
