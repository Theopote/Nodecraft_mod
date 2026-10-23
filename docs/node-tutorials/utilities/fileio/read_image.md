# Read Image

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.fileio.read_image` |
| 分类 | `utilities.fileio` |
| 显示名称 | Read Image |
| 效果 | `FILE_IO` |

## 作用

读本地图像为 IMAGE（元数据优先安全上限）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Path | FILE_PATH |
| 入 | Downsample Step | INTEGER |
| 入 | Read Mode | STRING |
| 出 | Path | FILE_PATH |
| 出 | Image | IMAGE |
| 出 | Width | INTEGER |
| 出 | Height | INTEGER |
| 出 | Aspect Ratio | DOUBLE |
| 出 | Grayscale Values | DOUBLE_LIST |
| 出 | Average Color | COLOR |
| 出 | Pixel Count | INTEGER |
| 出 | Sample Step | INTEGER |
| 出 | Was Downsampled | BOOLEAN |
| 出 | Error | STRING |
| 出 | Valid | BOOLEAN |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Read Image].Path
[Read Image].Path ──► [下游].…
```
