# Facade Grid

| 字段 | 值 |
|---|---|
| 节点 ID | `pattern.grid.facade_grid` |
| 分类 | `pattern.grid` |
| 显示名称 | Facade Grid |
| 效果 | `PURE` |

## 作用

在盒体面上生成立面单元格中心与边界路径。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Columns / Rows / Margin X·Y | BOX_FACE / INTEGER / DOUBLE |
| 出 | Center Points / Cell Boundaries / Cell Width·Height / Cell Count | … |

## 使用步骤

1. 接入几何或点列，以及变换/阵列参数。
2. 输出接到下游放置、预览或体素化。

## 典型接法

```text
[上游].… ──► [Facade Grid].Face
[Facade Grid].Center Points ──► [下游].…
```
