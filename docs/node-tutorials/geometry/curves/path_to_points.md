# Extract Path Points

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.path_to_points` |
| 分类 | `geometry.curves` |
| 显示名称 | Extract Path Points |
| 效果 | `PURE` |

## 作用

取出路径已有顶点/采样点。**不重采样**——要等距采样用 [Resample Path](./resample_path.md)。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path | `input_path` | PATH |
| 出 | Points / Count | … | POINT_LIST / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Extract Path Points].Path
[Extract Path Points].Points ──► [下游].…
```
