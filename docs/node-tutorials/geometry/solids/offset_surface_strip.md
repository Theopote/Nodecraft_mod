# Offset Surface Strip

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.offset_surface_strip` |
| 分类 | `geometry.solids` |
| 显示名称 | Offset Surface Strip |
| 效果 | `PURE` |

## 作用

曲面条带沿法向有符号偏移，输出单层偏移条带。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Surface Strip / Distance | … | SURFACE_STRIP / DOUBLE |
| 出 | Surface Strip / Region / Distance / Section Count | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Offset Surface Strip].Surface Strip
[Offset Surface Strip].Surface Strip ──► [下游].…
```
