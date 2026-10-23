# Explode Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.explode_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Explode Path |
| 效果 | `PURE` |

## 作用

拆成每段一条 PATH 的列表。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path | … | PATH |
| 出 | Segments / Count | … | PATH_LIST / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Explode Path].Path
[Explode Path].Segments ──► [下游].…
```
