# For Each Loop

| 字段 | 值 |
|---|---|
| 节点 ID | `flow.loop.for_each` |
| 分类 | `flow.loop` |
| 显示名称 | For Each Loop |
| 效果 | `PURE` |

## 作用

按列表逐项驱动执行流：对每个元素发一次 **Exec Body**，并输出当前 **Item**（元素类型 `T`）与 **Index**（从 0 起）。全部跑完（或列表为空 / Enabled 关闭）后发一次 **Exec Complete**。

适合「对每个点 / 每段路径 / 每个方块做同一套逻辑」的场景。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec In | `exec_in` | EXEC | 上游执行脉冲，启动本轮循环 |
| List | `input_list` | LIST（元素 `T`） | 要遍历的列表 |
| Enabled | `input_enabled` | BOOLEAN | 是否启用迭代；也可在属性面板设（默认 `true`） |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec Body | `exec_body` | EXEC | 每个列表项触发一次 |
| Exec Complete | `exec_complete` | EXEC | 全部结束后触发（空列表或未启用时也会触发一次） |
| Item | `output_item` | ANY（`T`） | 当前项，类型与 List 元素绑定 |
| Index | `output_index` | INTEGER | 当前下标（0-based） |
| Iteration Count | `output_count` | INTEGER | 本轮计划体脉冲次数（计划值，不等于「已完成」计数器） |
| Valid | `output_valid` | BOOLEAN | 预检是否通过 |
| Error | `output_error` | STRING | 未能运行时的原因 |

## 使用步骤

1. 把触发源 EXEC 接到 **Exec In**。
2. 把列表接到 **List**（例如点列、方块列表）。
3. 确认 **Enabled** 为 true（默认即可）。
4. 把循环体内要执行的节点接到 **Exec Body**，用 **Item** / **Index** 作为体逻辑输入。
5. 把「全部结束后」的收尾（如 Apply、汇总）接到 **Exec Complete**。

## 典型接法

```text
[Manual Trigger].Exec ──► [For Each Loop].Exec In
[点列表].Out           ──► [For Each Loop].List

[For Each Loop].Exec Body ──► [按 Item 放置 / 变换].Exec
[For Each Loop].Item      ──► [按 Item 放置 / 变换].Point
[For Each Loop].Index     ──► （可选）编号、偏移计算

[For Each Loop].Exec Complete ──► [Apply Changes].Exec
```

## 注意事项

- **Enabled = false** 或 **空列表**：Body 0 次，仍会发一次 **Exec Complete**。
- 列表长度不能超过 `MAX_LOOP_ITERATIONS`（当前 100 000）；超限则失败，看 **Valid** / **Error**。
- **Enabled** 若连了线且值为非法 / null，会 fail-closed（不跑循环），不会静默回退到属性默认值。
- **Item** 的类型 `T` 与 **List** 元素类型绑定，下游应按同类型接线。
