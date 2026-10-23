# Dimension Info

| 字段 | 值 |
|---|---|
| 节点 ID | `input.context.dimension_info` |
| 分类 | `input.context` |
| 显示名称 | Dimension Info |
| 效果 | `WORLD_READ`（只读上下文） |

## 作用

读取当前活动世界的**维度 ID** 与基础特征（主世界 / 下界 / 末地、是否有天空光、是否有天花板）。用于按维度切换材质、高度规则或建造逻辑。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Valid | `output_valid` | BOOLEAN | 世界上下文是否可用 |
| Dimension ID | `output_dimension_id` | STRING | 维度标识符 |
| Is Overworld | `output_is_overworld` | BOOLEAN | 是否主世界 |
| Is Nether | `output_is_nether` | BOOLEAN | 是否下界 |
| Is End | `output_is_end` | BOOLEAN | 是否末地 |
| Has Skylight | `output_has_skylight` | BOOLEAN | 是否有天空光 |
| Has Ceiling | `output_has_ceiling` | BOOLEAN | 是否有天花板（如下界） |

## 使用步骤

1. 拖入节点，确认进入了目标维度。
2. 用 **Is Nether** / **Is Overworld** 等接到 Branch 或逻辑节点。
3. 需要原始 ID 字符串时用 **Dimension ID**（自定义维度更常用）。

## 典型接法

```text
[Dimension Info].Is Nether ──► [Branch].Condition
[Branch].Exec True         ──► 下界材质 / 高度限制逻辑
[Branch].Exec False        ──► 主世界默认逻辑
```

## 注意事项

- 无活动世界时 Valid = false，布尔特征不可信。
- 模组自定义维度可能三个 Is\* 都为 false，此时以 **Dimension ID** 为准。
