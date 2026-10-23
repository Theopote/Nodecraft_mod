# Frame Local Variable

| 字段 | 值 |
|---|---|
| 节点 ID | `variable.frame_local` |
| 分类 | `variable` |
| 显示名称 | Frame Local Variable |
| 效果 | `CONTEXT_WRITE` |

## 作用

帧局部命名空间读写；命令顺序：校验→清帧→写→读。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Frame | STRING |
| 入 | Name | STRING |
| 入 | Write | BOOLEAN |
| 入 | Clear Frame | BOOLEAN |
| 出 | Exists | BOOLEAN |
| 出 | Frame | STRING |
| 出 | Name | STRING |
| 出 | Frame Size | INTEGER |
| 出 | Valid | BOOLEAN |
| 出 | Cleared | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Frame Local Variable].Frame
[Frame Local Variable].Exists ──► [下游].…
```
