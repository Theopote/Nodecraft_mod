# Value List（Dropdown）

| 字段 | 值 |
|---|---|
| 节点 ID | `input.values.dropdown` |
| 分类 | `input.values` |
| 显示名称 | Value List |
| 效果 | `PURE` |

## 作用

从一组字符串选项里选一项，输出 **Index** + **Value（文本）**。选项可来自属性 CSV，或由上游 **Options**（STRING_LIST）覆盖。

## 端点

### 输入

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Index | `input_index` | INTEGER | 可选；覆盖所选下标；连了越界则 fail-closed |
| Options | `input_options` | STRING_LIST | 可选；覆盖选项列表；连了非法则 fail-closed（不回退 CSV） |

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Index | `output_index` | INTEGER | 当前选中下标 |
| Value | `output_value` | STRING | 选中项文本 |
| Options | `output_options` | STRING_LIST | 解析后的选项列表 |
| Valid | `output_valid` | BOOLEAN | 选项可用且可选驱动合法 |
| Error | `output_error` | STRING | Valid 为 false 时的原因 |

### 属性

| 属性 | 说明 |
|---|---|
| Options（CSV） | 未接 Options 口时的选项源 |
| Selected Index | 未接 Index 口时的默认下标 |

## 使用步骤

1. 在属性里用 CSV 写选项，或把 STRING_LIST 接到 **Options**。
2. 用属性 / 输入口设定 **Index**。
3. 把 **Value** 接到需要字符串枚举的下游；需要数字分支时用 **Index**。

## 典型接法

```text
[Value List].Value ──► [材质名 / 模式字符串]
[Value List].Index ──► [Compare / Switch 类逻辑]
```

## 注意事项

- 连接了 Options / Index 后，非法输入会 **fail-closed**，不会悄悄用 CSV / 属性兜底。
- 未连接 Index 时，属性 Selected Index 越界会按契约夹紧（以 Valid 为准）。
