# Beam Along Path

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.architectural_primitives.beam_along_path` |
| 分类 | `geometry.architectural_primitives` |
| 显示名称 | Beam Along Path |
| 效果 | `PURE` |

## 作用

路径**每段一根梁盒**（非连续扫掠）。Width 沿路径右侧，Height 沿局部 up（跟路径倾角）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Path / Width / Height / Offset | PATH / DOUBLE |
| 出 | Geometry / Frames / Count | GEOMETRY / FRAME_LIST / INTEGER |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Beam Along Path].Path
[Beam Along Path].Geometry ──► [下游].…
```
