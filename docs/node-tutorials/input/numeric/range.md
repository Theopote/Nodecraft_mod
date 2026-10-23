# Domain Input

| 字段 | 值 |
|---|---|
| 节点 ID | `input.numeric.range` |
| 分类 | `input.numeric` |
| 显示名称 | Domain Input |
| 效果 | `PURE` |

## 作用

定义有向数值域 **Start → End**，输出 Domain、起止、有向跨度 Span（End − Start）。用于采样区间、映射域、动画时间窗等。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Domain | `output_domain` | NUMERIC_RANGE | 有向域（Valid 时） |
| Start | `output_start` | DOUBLE | 起点 |
| End | `output_end` | DOUBLE | 终点 |
| Span | `output_span` | DOUBLE | 有向跨度 End − Start |
| Valid | `output_valid` | BOOLEAN | Start/End/Span 均为有限 |
| Error | `output_error` | STRING | Valid 为 false 时原因 |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| Start / End | `0` / `1` | 域端点 |
| Precision | `2` | 面板显示小数位 |

## 使用步骤

1. 设 Start、End（允许 End < Start，Span 为负，表示反向域）。
2. 需要整包域类型时接 **Domain**；只要端点用 Start/End。
3. 用 **Valid** 做 fail-closed 分支。

## 典型接法

```text
[Domain Input].Domain ──► [需要 NUMERIC_RANGE 的采样 / 映射节点]
[Domain Input].Start  ──► [Remap].From Min
[Domain Input].End    ──► [Remap].From Max
```

## 注意事项

- Span 是**有向**的，不是绝对值长度。
- 非有限 Start/End → Valid false。
