# Column

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.column` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Column |
| 效果 | `PURE` |

## 作用

单柱：Frame（原点=底，Y=上）或 Base 点二选一。Shape：`cylinder` / `box` / `frustum`（Top Scale 控制锥顶）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Frame / Base（恰连其一）/ Height / Radius / Shape / Top Scale | … |
| 出 | Geometry / Base / Top / Frame | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Column].Frame
[Column].Geometry ──► [下游].…
```
