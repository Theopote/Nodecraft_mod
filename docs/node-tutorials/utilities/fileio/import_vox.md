# Import VOX

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.fileio.import_vox` |
| 分类 | `utilities.fileio` |
| 显示名称 | Import VOX |
| 效果 | `FILE_IO` |

## 作用

导入体素模型为坐标/颜色/调色板索引（非完整 MagicaVoxel 场景图）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Path | FILE_PATH |
| 入 | Origin | BLOCK_POS |
| 入 | Z Up To Y | BOOLEAN |
| 出 | Path | FILE_PATH |
| 出 | Blocks | BLOCK_LIST |
| 出 | Voxel Colors | COLOR_LIST |
| 出 | Color Indices | INTEGER_LIST |
| 出 | Size X | INTEGER |
| 出 | Size Y | INTEGER |
| 出 | Size Z | INTEGER |
| 出 | Voxel Count | INTEGER |
| 出 | VOX Version | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Import VOX].Path
[Import VOX].Path ──► [下游].…
```
