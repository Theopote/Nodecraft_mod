# Facade Panel Array

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.facade_panel_array` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Facade Panel Array |
| 效果 | `PURE` |

## 作用

盒体面上的矩形幕墙/饰面板阵列（实体面板，非切割体）。布局模式同窗洞阵列。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Face / Columns / Rows / Panel Width·Height / Margin / Thickness / Offset | … |
| 入 | Layout Mode / Gaps / Bay Width | … |
| 出 | Geometry / Frames / Centers / Indices / Count | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Facade Panel Array].Face
[Facade Panel Array].Geometry ──► [下游].…
```
