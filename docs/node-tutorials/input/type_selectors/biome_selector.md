# Biome Selector

| 字段 | 值 |
|---|---|
| 节点 ID | `input.type_selectors.biome_selector` |
| 分类 | `input.type_selectors` |
| 显示名称 | Biome Selector |
| 效果 | `PURE` |

## 作用

选定一个 **生物群系 ID**，供群系感知的生成 / 材质 / 地形工作流使用。默认 `minecraft:plains`。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Biome | `output_biome_id` | BIOME | 完整群系 ID |
| Namespace | `output_namespace` | STRING | 命名空间 |
| Biome Path | `output_biome_path` | STRING | 路径段 |
| Is Modded | `output_is_modded` | BOOLEAN | 是否非 `minecraft` 命名空间 |
| Valid | `output_valid` | BOOLEAN | 选择是否有效 |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| Selected Biome | `minecraft:plains` | 当前选中 ID |
| Allow Modded Biomes | `true` | 是否在搜索中包含模组群系 |

### 选择器分类（UI）

All / Overworld / Ocean / Nether / End / Modded；快捷项含 plains、forest、desert、taiga、jungle、swamp、badlands、cherry_grove。

## 使用步骤

1. 打开选择器，按维度类分类或搜索群系 ID。
2. 把 **Biome** 接到群系过滤、地形、生成条件等节点。

## 典型接法

```text
[Biome Selector].Biome     ──► [群系感知生成 / 查询].Biome
[Dimension Info].Is Nether ──► （可选）先分支再选下界群系
```

## 注意事项

- 选定群系 ≠「当前脚下群系」；读当前世界群系请用 `world.query` / `world.read` 相关节点。
- 模组群系依赖对应模组注册表是否已加载。
