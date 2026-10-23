# Sequence

| 字段 | 值 |
|---|---|
| 节点 ID | `flow.control.sequence` |
| 分类 | `flow.control` |
| 显示名称 | Sequence |
| 效果 | `PURE` |

## 作用

收到一次 EXEC 后，按顺序触发 **Exec Step 1..N**（N 由 **Step Count** 决定，范围 1～8）。可选 **Signal** 会复制到每个激活步骤的数据口（透传 `T`）。

适合「先预览、再写入、再清理」这类需要固定顺序、多段 EXEC 的流程。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec In | `exec_in` | EXEC | 上游执行脉冲 |
| Signal | `input_signal` | ANY（透传 `T`） | 可选；复制到各 Step 数据口 |
| Step Count | `input_step_count` | INTEGER | 激活步数，精确整数 1～8（默认 2） |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec Step 1..8 | `exec_step_1` … `exec_step_8` | EXEC | 按序号依次触发；仅前 N 步有效 |
| Step 1..8 | `output_step_1` … `output_step_8` | ANY（`T`） | 对应步骤的 Signal 透传 |
| Active Step Count | `output_active_step_count` | INTEGER | 实际激活的步数 |
| Active Step Indexes | `output_active_steps` | INTEGER_LIST | 激活步骤的 1-based 下标列表 |
| Valid | `output_valid` | BOOLEAN | Step Count 预检是否成功 |
| Error | `output_error` | STRING | 未能运行时的原因 |

## 使用步骤

1. 把触发源 EXEC 接到 **Exec In**。
2. 设置 **Step Count**（属性或上游整数口），例如 `3`。
3. 把第 1、2、3 步要跑的节点分别接到 **Exec Step 1 / 2 / 3**。
4. 若各步需要同一份输入数据，接到 **Signal**，再从对应 **Step N** 数据口引出。

## 典型接法

```text
[Manual Trigger].Exec ──► [Sequence].Exec In
                      Step Count = 3

[Sequence].Exec Step 1 ──► [Preview Geometry].Exec
[Sequence].Exec Step 2 ──► [Apply Changes].Exec
[Sequence].Exec Step 3 ──► [清理 / 日志节点].Exec
```

## 注意事项

- Step Count 必须是精确整数且落在 1～8；非法值时 **Valid** 为 false，看 **Error**。
- 未激活的 Step 口不会发出 EXEC（例如 Count=2 时 Step 3～8 不火）。
- Signal 是可选透传，不参与是否执行的判定。
