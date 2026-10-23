# SDF Noise Displace

| 字段 | 值 |
|---|---|
| 节点 ID | `geometry.boolean.sdf_noise_displace` |
| 分类 | `geometry.sdf` |
| 显示名称 | SDF Noise Displace |
| 效果 | `PURE` |

## 作用

对 SDF 施加确定性伪噪声位移（表面起伏）。

## 端点

| 方向 | 显示名 | 端口 ID | 类型 |
|---|---|---|---|
| 入 | SDF | `input_sdf` | SDF |
| 入 | Amplitude / Frequency / Seed | … | DOUBLE / DOUBLE / INTEGER（可选覆盖面板） |
| 出 | SDF | `output_sdf` | SDF |
| 出 | Valid / Error | `output_valid` / `output_error` | BOOLEAN / STRING |

## 使用步骤

1. 按端点表接好构造/编辑所需输入。
2. 检查 Valid；Geometry / Path / Profile 等接到下游。

## 典型接法

```text
[上游].… ──► [SDF Noise Displace].SDF
[SDF Noise Displace].SDF ──► [下游].…
```

## 注意事项

- 面板另有 Offset；表达式深度超预算会失败。
