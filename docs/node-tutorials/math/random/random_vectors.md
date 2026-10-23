# Random Vectors

| 字段 | 值 |
|---|---|
| 节点 ID | `math.random.random_vectors` |
| 分类 | `math.random` |
| 显示名称 | Random Vectors |
| 效果 | `PURE` |

## 作用

包围盒内确定性随机向量列表。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Min Corner | VECTOR |
| 入 | Max Corner | VECTOR |
| 入 | Count | INTEGER |
| 入 | Seed | INTEGER |
| 出 | Vectors | VECTOR_LIST |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Random Vectors].Min Corner
[Random Vectors].Vectors ──► [下游].…
```
