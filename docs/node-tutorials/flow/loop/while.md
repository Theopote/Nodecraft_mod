# While Loop

| 字段 | 值 |
|---|---|
| 节点 ID | `flow.loop.while` |
| 分类 | `flow.loop` |
| 显示名称 | While Loop |
| 效果 | `CONTEXT_WRITE` |

## 作用

条件驱动的 EXEC 循环：在 **Condition** 为 true 且未超过 **Max Iterations** 时发 **Exec Body**；条件变 false 或撞到上限时发 **Exec Complete**。

用法要点：把 **Exec Body** 再绕回 **Exec In**（并在体逻辑里更新会改变 Condition 的状态），每次脉冲都会重新求值 Condition。迭代计数存在本次执行 run 的上下文里，会话结束后会清掉，同一次 run 里再次进入会开新会话。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec In | `exec_in` | EXEC | 进入循环 / 回绕脉冲 |
| Condition | `input_condition` | BOOLEAN | **必填**；未连接或非法则无效 |
| Max Iterations | `input_max_iterations` | INTEGER | 上限，精确整数 `1 .. MAX_LOOP_ITERATIONS`（属性默认 `256`） |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec Body | `exec_body` | EXEC | Condition 为 true 且未达上限时触发 |
| Exec Complete | `exec_complete` | EXEC | Condition 为 false，或已达 Max Iterations |
| Iterations | `output_iterations` | INTEGER | 本会话已发出的 Body 次数 |
| Terminated By Condition | `output_terminated_by_condition` | BOOLEAN | 因 Condition 变 false 而结束 |
| Hit Limit | `output_hit_limit` | BOOLEAN | 因达到最大迭代次数而结束 |
| Valid | `output_valid` | BOOLEAN | 预检是否成功 |
| Error | `output_error` | STRING | 未能运行时的原因 |

## 使用步骤

1. 用 Manual Trigger（或其它 EXEC）接到 **Exec In** 启动。
2. 接好 **Condition**（必须是严格布尔；可用 Compare / Boolean Toggle 等）。
3. 设好 **Max Iterations**，避免死循环拖垮执行。
4. 把循环体接到 **Exec Body**，并在体逻辑里改变会影响 Condition 的数据（变量、计数等）。
5. 将 **Exec Body** 绕回本节点的 **Exec In**（或接到会再次脉冲本节点的链）。
6. 把结束后的收尾接到 **Exec Complete**；可用 **Hit Limit** / **Terminated By Condition** 区分结束原因。

## 典型接法

```text
[Manual Trigger].Exec ──► [While Loop].Exec In
[条件布尔].Out         ──► [While Loop].Condition
                     Max Iterations = 64

[While Loop].Exec Body ──► [更新条件 / 做一步工作].Exec
                       └──（再绕回）──► [While Loop].Exec In

[While Loop].Exec Complete ──► [Apply / 收尾].Exec
```

## 注意事项

- **Condition 必填**；未连接、null 或非布尔 → **Valid = false**，循环不跑。
- Max Iterations 必须是精确整数，范围 `1 .. 100000`（`GenerationLimits.MAX_LOOP_ITERATIONS`）。
- 迭代计数在 run 上下文中，**不进存档图**；Complete 后会清空，同 run 再次进入算新会话。
- 若 Condition 一直为 true，会走到 **Hit Limit** 后 Complete，而不是无限转。
- 与 For Each 不同：While 没有 Item/Index，需要自己在体里维护状态（可用 `variable` 类节点）。
