# Entity Type Selector

| 字段 | 值 |
|---|---|
| 节点 ID | `input.type_selectors.entity_type_selector` |
| 分类 | `input.type_selectors` |
| 显示名称 | Entity Type Selector |
| 效果 | `PURE` |

## 作用

搜索并选定一个 Minecraft **实体类型** ID，供生成实体、过滤查询等使用。默认 `minecraft:pig`。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Entity Type | `output_entity_id` | ENTITY_TYPE | 完整 ID |
| Namespace | `output_namespace` | STRING | 命名空间 |
| Entity Path | `output_entity_path` | STRING | 路径段 |
| Is Modded | `output_is_modded` | BOOLEAN | 是否非 `minecraft` 命名空间 |
| Valid | `output_valid` | BOOLEAN | 选择是否有效 |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| Selected Entity | `minecraft:pig` | 当前选中 ID |
| Allow Modded Entities | `true` | 是否在搜索中包含模组实体 |

### 选择器分类（UI）

All / Hostile / Passive / Boss / Utility / Modded；快捷项含 pig、villager、armor_stand、zombie 等。

## 使用步骤

1. 打开选择器，按 Hostile / Passive 等分类或搜索。
2. 把 **Entity Type** 接到 `Spawn Entity` 或世界查询 / 过滤节点。

## 典型接法

```text
[Entity Type Selector].Entity Type ──► [Spawn Entity].Entity Type
[Player Raycast].Hit Position      ──► [Spawn Entity].Position
```

## 注意事项

- 输出的是**类型 ID**，不是世界里的活实体实例。
- 部分实体有生成限制（如 boss、特殊骑乘），失败看下游节点 Error。
