# Reflect Vector

| 字段 | 值 |
|---|---|
| 节点 ID | `reference.vectors.reflect` |
| 分类 | `reference.vectors` |
| 显示名称 | Reflect Vector |
| 效果 | `PURE` |

## 作用

按 `v − 2(v·n)n` 反射；Normal 会先归一化。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Vector | … | VECTOR |
| 入 | Normal | … | VECTOR |
| 出 | Reflected | … | VECTOR |
| 出 | Valid / Error | … | BOOLEAN / STRING |

## 使用步骤

1. 设置面板参数或接上游值。
2. 把输出接到需要该类型的下游节点。

## 典型接法

```text
[上游].… ──► [Reflect Vector].Vector
[Reflect Vector].Reflected ──► [下游].…
```
