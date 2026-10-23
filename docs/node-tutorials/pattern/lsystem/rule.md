# L-System Rule

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.lsystem.rule` |
| 分类 | `pattern.lsystem` |
| 显示名称 | L-System Rule |
| 效果 | `PURE` |

## 作用

构造一条产生式：Symbol → Production，Weight 为相对权重。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Symbol / Production / Weight | STRING / STRING / DOUBLE |
| 出 | Rule | L_SYSTEM_RULE |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [L-System Rule].Symbol
[L-System Rule].Rule ──► [下游].…
```
