# Text Input

| 字段 | 值 |
|---|---|
| 节点 ID | `input.values.text_input` |
| 分类 | `input.values` |
| 显示名称 | Text Input |
| 效果 | `PURE` |

## 作用

在节点上输入**多行文本**，输出字符串与长度。用于命令字符串、标记文字、文件内容草稿等。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Text | `output_text` | STRING | 输入内容 |
| Length | `output_length` | INTEGER | 字符长度 |

## 使用步骤

1. 拖入节点，在文本框里输入内容。
2. 把 **Text** 接到需要 STRING 的下游（如 Write Sign、Execute Command、导出等）。
3. 需要长度校验时用 **Length**。

## 典型接法

```text
[Text Input].Text ──► [Write Sign Text / Execute Command / …].Text
```

## 注意事项

- 超长文本下游可能有各自上限（如命令长度、NBT 限制），失败看下游 Error。
