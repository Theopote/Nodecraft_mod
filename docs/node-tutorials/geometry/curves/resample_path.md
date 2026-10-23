# Resample Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.resample_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Resample Path |
| 效果 | `PURE` |

## 作用

按弧长重采样：Mode=`Count` 或 `Spacing`。主输出 PATH。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / Mode / Spacing / Count | … | PATH / STRING / DOUBLE / INTEGER |
| 出 | Path / Points / Count / Length | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Resample Path].Path
[Resample Path].Path ──► [下游].…
```
