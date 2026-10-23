# Do Once

| 字段 | 值 |
|---|---|
| 节点 ID | `flow.control.do_once` |
| 分类 | `flow.control` |
| 显示名称 | Do Once |
| 效果 | `CONTEXT_WRITE` |

## 作用

在**同一次图执行 run** 里，只放行第一次 EXEC；之后的脉冲走 **Exec Blocked**。闸门状态存在执行上下文（`ExecutionRunGuard`）里，**不会**写进存档图。

需要「整次运行只提交一次世界写入 / 只弹一次逻辑」时用它。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec In | `exec_in` | EXEC | 上游执行脉冲 |
| Signal | `input_signal` | ANY（透传 `T`） | 可选载荷；不参与放行判定 |
| Reset | `input_reset` | EXEC | 可选；脉冲到达时清掉本 run 的闸门 |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec Out | `exec_out` | EXEC | 首次放行时触发 |
| Exec Blocked | `exec_blocked` | EXEC | 闸门已消耗时触发 |
| First Pass | `output_first_pass` | ANY（`T`） | 首次通过时的 Signal |
| Blocked | `output_blocked` | ANY（`T`） | 被挡时的 Signal |
| Did Execute | `output_did_execute` | BOOLEAN | 本次脉冲是否通过闸门 |
| Has Executed | `output_has_executed` | BOOLEAN | 闸门是否已被消耗 |
| Valid | `output_valid` | BOOLEAN | 预检是否成功 |
| Error | `output_error` | STRING | 未能运行时的原因 |

## 使用步骤

1. 把可能重复触发的 EXEC 接到 **Exec In**。
2. 把真正只想跑一次的下游接到 **Exec Out**。
3. （可选）把「已被挡过」的分支接到 **Exec Blocked**（例如提示或跳过）。
4. 若同一次 run 内需要再次允许通过，向 **Reset** 发一次 EXEC 脉冲。

## 典型接法

```text
[While Loop / 多次触发源].Exec ──► [Do Once].Exec In
[Do Once].Exec Out           ──► [Apply Changes].Exec
[Do Once].Exec Blocked       ──► [Debug Log]（可选）
```

## 注意事项

- 闸门按 **execution run** 计，不是按编辑器会话永久锁死；新的一次图运行会重新开始。
- 状态不进 SavedGraph，重开图不会「记得上次已经执行过」。
- 必须有执行上下文；脱离 run 时看 **Valid** / **Error**。
- Signal 未连接也不影响 EXEC 放行 / 拦截。
