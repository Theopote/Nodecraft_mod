# Face Edge To Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.curves.edge_to_curve` |
| 分类 | `geometry.curves` |
| 显示名称 | Face Edge To Path |
| 效果 | `PURE` |

## 作用

盒体面边（LINE）转 PATH，并输出端点与角点索引。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Edge | `input_edge` | LINE |
| 入 | Start / End Corner Index | … | INTEGER（可选透传） |
| 出 | Path / Start Point / End Point / 角点索引 | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Face Edge To Path].Edge
[Face Edge To Path].Path ──► [下游].…
```
