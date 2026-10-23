# Number Sequence

| 字段 | 值 |
|---|---|
| 节点 ID | `math.sequence.range` |
| 分类 | `math.sequence` |
| 显示名称 | Number Sequence |
| 效果 | `PURE` |

## 作用

Start→End 按 Step 生成离散 DOUBLE_LIST（非连续域）。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Start | DOUBLE |
| 入 | End | DOUBLE |
| 入 | Step | DOUBLE |
| 出 | Numbers | DOUBLE_LIST |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Number Sequence].Start
[Number Sequence].Numbers ──► [下游].…
```
