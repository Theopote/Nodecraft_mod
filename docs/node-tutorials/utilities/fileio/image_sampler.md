# Image Sampler

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.fileio.image_sampler` |
| 分类 | `utilities.fileio` |
| 显示名称 | Image Sampler |
| 效果 | `PURE` |

## 作用

按 UV 或像素采样 IMAGE；UV 0..1 映射到含端点像素。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Image | IMAGE |
| 入 | U | DOUBLE |
| 入 | V | DOUBLE |
| 入 | X | DOUBLE |
| 入 | Y | DOUBLE |
| 出 | Color | COLOR |
| 出 | R | DOUBLE |
| 出 | G | DOUBLE |
| 出 | B | DOUBLE |
| 出 | A | DOUBLE |
| 出 | Grayscale | DOUBLE |
| 出 | Sample X | DOUBLE |
| 出 | Sample Y | DOUBLE |
| 出 | Resolved Mode | STRING |
| 出 | Error | STRING |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Image Sampler].Image
[Image Sampler].Color ──► [下游].…
```
