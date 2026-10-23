# Stopwatch

| 字段 | 值 |
|---|---|
| 节点 ID | `output.debug.execution_timer` |
| 分类 | `output.debug` |
| 显示名称 | Stopwatch |
| 效果 | `CONTEXT_WRITE` |

## 作用

在 Start / Stop 脉冲之间测墙钟时间；Reset 清零。Auto Reset 可在 Stop 后自动复位。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Start / Stop / Reset / Auto Reset | EXEC / BOOLEAN |
| 出 | Execution·Total·Average Time / Count / Formatted Time | DOUBLE / INTEGER / STRING |

## 使用步骤

1. 按端点表连接输入。
2. 查看输出并接到下游。

## 典型接法

```text
[上游].… ──► [Stopwatch].Start
[Stopwatch].Execution ──► [下游].…
```
