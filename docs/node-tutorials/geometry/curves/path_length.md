# Path Length

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.path_length` |
| 分类 | `geometry.curves` |
| 显示名称 | Path Length |
| 效果 | `PURE` |

## 作用

计算路径总长。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path | `input_path` | PATH |
| 出 | Length | `output_length` | DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Path Length].Path
[Path Length].Length ──► [下游].…
```
