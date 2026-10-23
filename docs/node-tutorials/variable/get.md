# Get Variable

| 字段 | 值 |
|---|---|
| 节点 ID | `variable.get` |
| 分类 | `variable` |
| 显示名称 | Get Variable |
| 效果 | `CONTEXT_READ` |

## 作用

按名读取；Exists 表示名存在（值可为 null）；绑定 T 须匹配槽类型。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Name | STRING |
| 出 | Exists | BOOLEAN |
| 出 | Name | STRING |
| 出 | Is Null | BOOLEAN |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [Get Variable].Name
[Get Variable].Exists ──► [下游].…
```
