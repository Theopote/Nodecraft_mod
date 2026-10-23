# Difference

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.difference` |
| 分类 | `geometry.boolean` |
| 显示名称 | Difference |
| 效果 | `PURE` |

## 作用

从 Base 减去 Cutter（延迟体素差集）。建筑洞口标准接法：墙体 → Base，窗/门阵列 Openings → Cutter。

## 示意图

```mermaid
flowchart LR
  Base[墙 / 实体] --> Diff[Difference]
  Cut[窗洞 / 门洞 / 拱洞] --> Diff
  Diff -->|延迟表达式| Geo[GEOMETRY]
  Geo --> Bake[Voxelize 或 Apply]
```

```text
  ████████████          ████  ██  ████
  ████████████   减去    ████      ████   →   挖空墙
  ████████████   刀具    ████  ██  ████
```

Valid 只表示表达式构造成功，**不保证**体素化后非空。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | Base Geometry | `input_base` | GEOMETRY |
| 入 | Cutter Geometry | `input_cutter` | GEOMETRY |
| 出 | Geometry | `output_geometry` | GEOMETRY |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. Base 接被减几何，Cutter 接刀具（可多洞合并后再接）。
2. 看 Valid；下游必须 Voxelize / Preview / Apply 才会在方块格上求值。

## 典型接法

```text
[Wall Slab].Wall Geometry ──► [Difference].Base
[Window Array].Openings ──► [Difference].Cutter
[Difference].Geometry ──► [Preview Geometry] / [Apply Changes]
```

## 注意事项

- 在 Minecraft 方块格上求值，非 CAD 精确曲面布尔。
