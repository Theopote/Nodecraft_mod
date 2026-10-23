# World Frame

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.frames.world_frame` |
| 分类 | `reference.frames` |
| 显示名称 | World Frame |
| 效果 | `PURE` |

## 作用

输出世界坐标系 FRAME：原点 `(0,0,0)`，轴为单位 +X / +Y / +Z。作为默认放置参照或 Transform 的起点。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Frame | `output_frame` | FRAME | 世界正交架 |

## 使用步骤

1. 拖入节点，把 **Frame** 接到需要世界系的放置 / 变换节点。

## 典型接法

```text
[World Frame].Frame ──► [Transform Frame].Frame
[World Frame].Frame ──► [Orient / Place 几何].Frame
```

## 注意事项

- 无输入、恒定输出；不读玩家位置（要玩家位姿用 context + Construct Frame）。
