# Manual Trigger

| 字段 | 值 |
|---|---|
| 节点 ID | `flow.control.manual_trigger` |
| 分类 | `flow.control` |
| 显示名称 | Manual Trigger |
| 效果 | `PURE` |

## 作用

画布上的手动执行入口：点击节点上的 **Trigger** 按钮后，发出**一次性** EXEC 脉冲，并提交一次手动图运行。它本身**不写世界**；真正改方块要靠下游的 Apply / World Write 等节点。

适合调试流程、按需提交建造图，而不用依赖自动预览刷新。

## 端点

### 输入

无（完全由节点 UI 按钮驱动）。

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Exec | `output_exec` | EXEC | 按下 Trigger 后发出的一次性执行脉冲 |

## 使用步骤

1. 从节点库 `flow.control` 拖入 **Manual Trigger**。
2. 把它的 **Exec** 接到你想手动启动的链（例如 Sequence / Branch / Apply Changes）。
3. 进入世界、保证图可运行后，点击节点上的 **Trigger**。
4. 看按钮旁状态：`Idle` → `Pending` / `Submitted`；失败会显示 `Failed` 与简短原因。

## 典型接法

```text
[Manual Trigger].Exec ──► [Sequence].Exec In
                     └──► 或直接 ──► [Apply Changes].Exec
```

带条件时：

```text
[Manual Trigger].Exec ──► [Branch].Exec In
[条件].Out            ──► [Branch].Condition
```

## 注意事项

- 每次点击只产生一发脉冲；不会卡在「一直为 true」。
- 提交失败（无活动图、未在世界中、调度拒绝等）会清掉 pending，避免一直停在 Pending。
- 本节点是 `PURE`：不会单独改世界；世界写入取决于图里是否已有 `WORLD_WRITE` 类下游。
- UI 状态（Pending / Submitted / Failed）是运行时反馈，不必当持久数据用。
