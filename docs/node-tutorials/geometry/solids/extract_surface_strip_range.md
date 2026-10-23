# Extract Surface Strip Range

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.solids.extract_surface_strip_range` |
| 分类 | `geometry.solids` |
| 显示名称 | Extract Surface Strip Range |
| 效果 | `PURE` |

## 作用

按归一化 U∈[0,1] 截取条带连续区段。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Surface Strip / Start / End | … | SURFACE_STRIP / DOUBLE / DOUBLE |
| 出 | Surface Strip / Section Count / Points Per Section / Start·End Section | … | … |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [Extract Surface Strip Range].Surface Strip
[Extract Surface Strip Range].Surface Strip ──► [下游].…
```
