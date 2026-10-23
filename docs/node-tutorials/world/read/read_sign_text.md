# Read Sign Text

| 字段 | 值 |
|---|---|
| 节点 ID | `world.read.read_sign_text` |
| 分类 | `world.read` |
| 显示名称 | Read Sign Text |
| 效果 | `WORLD_READ` |

## 作用

读告示牌正面纯文本。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Coordinate | BLOCK_POS |
| 出 | Text Lines | STRING_LIST |
| 出 | Combined Text | STRING |
| 出 | Is Sign | BOOLEAN |
| 出 | Sign Type | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |


## 使用步骤

1. 接好查询/预览对象（区域、坐标、几何等）。
2. 运行后查看输出；预览类不写世界，清理用 Clear Preview。

## 典型接法

```text
[上游].… ──► [Read Sign Text].Coordinate
[Read Sign Text].Text Lines ──► [下游].…
```

## 注意事项

- 需要世界上下文；无世界时 Valid 可能为 false。
