# File Path Input

| 字段 | 值 |
|---|---|
| 节点 ID | `input.values.file_path` |
| 分类 | `input.values` |
| 显示名称 | File Path Input |
| 效果 | `PURE` |

## 作用

选择或输入本地文件路径，拆成 Path / 文本 / 目录 / 文件名 / 扩展名，供读写、导入导出节点使用。节点 UI 提供 Browse / Clear 与 Open/Save 对话框模式。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Path | `output_path` | FILE_PATH | 规范化后的路径 |
| Text | `output_text` | STRING | 同路径的字符串形式 |
| Directory | `output_directory` | STRING | 父目录 |
| File Name | `output_filename` | STRING | 带扩展名的文件名 |
| Extension | `output_extension` | STRING | 小写扩展名 |
| Has Value | `output_has_value` | BOOLEAN | 是否选了非空路径 |
| Valid | `output_valid` | BOOLEAN | 非空且语法上可解析（**不检查文件是否真实存在**） |

### 常用属性

| 属性 | 说明 |
|---|---|
| Dialog Mode | OPEN / SAVE 等 |
| Initial Directory | 对话框起始目录 |
| Filter | 扩展名过滤 |
| Default Extension | Save 时默认追加的扩展名（如 `.json`） |

## 使用步骤

1. 拖入节点，点 Browse 选文件，或直接粘贴路径。
2. 把 **Path**（或 **Text**）接到 utilities.fileio / export 等下游。
3. 用 **Has Value** / **Valid** 做前置检查。

## 典型接法

```text
[File Path Input].Path ──► [读/写文件节点].Path
[File Path Input].Has Value ──► [Branch].Condition
```

## 注意事项

- **Valid 只表示路径语法可解析**，不保证磁盘上文件存在或可读写。
- 路径会做绝对化与 normalize；跨平台分隔符以 JVM `Path` 为准。
