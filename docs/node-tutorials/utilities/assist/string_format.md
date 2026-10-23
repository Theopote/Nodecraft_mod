# String Format

| 字段 | 值 |
|---|---|
| 节点 ID | `utilities.assist.string_format` |
| 分类 | `utilities.assist` |
| 显示名称 | String Format |
| 效果 | `PURE` |

## 作用

用 {0}/{1}… 占位符格式化字符串。

## 端点

| 方向 | 显示名 | 类型 |
|---|---|---|
| 入 | Template | STRING |
| 入 | Values | LIST |
| 入 | Value 0 | ANY |
| 入 | Value 1 | ANY |
| 入 | Value 2 | ANY |
| 出 | Text | STRING |
| 出 | Length | INTEGER |
| 出 | Used Value Count | INTEGER |
| 出 | Missing Placeholder Count | INTEGER |
| 出 | Message | STRING |
| 出 | Valid | BOOLEAN |
| 出 | Error | STRING |

## 使用步骤

1. 接好所需输入（见端点表；可选端口可留空用面板默认）。
2. 执行/刷新图后查看主输出；若有 Valid/Error，先确认 Valid。

## 典型接法

```text
[上游].… ──► [String Format].Template
[String Format].Text ──► [下游].…
```
