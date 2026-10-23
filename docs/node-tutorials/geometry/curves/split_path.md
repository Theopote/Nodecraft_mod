# Split Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.split_path` |
| 分类 | `geometry.curves` |
| 显示名称 | Split Path |
| 效果 | `PURE` |

## 作用

在归一化参数（0..1，不含端点）处拆成两段。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Path / Parameter | … | PATH / DOUBLE |
| 出 | Path A / Path B / Parameter | … | PATH / PATH / DOUBLE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Split Path].Path
[Split Path].Path A ──► [下游].…
```
