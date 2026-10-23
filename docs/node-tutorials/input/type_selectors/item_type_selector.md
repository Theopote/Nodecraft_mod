# Item Type Selector

| 字段 | 值 |
|---|---|
| 节点 ID | `input.type_selectors.item_type_selector` |
| 分类 | `input.type_selectors` |
| 显示名称 | Item Type Selector |
| 效果 | `PURE` |

## 作用

搜索并选定一个 Minecraft **物品类型** ID。默认 `minecraft:stone`。用于物品相关写入、展示或与「方块同名物品」区分的工作流。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Item Type | `output_item_id` | ITEM_TYPE | 完整 ID |
| Namespace | `output_namespace` | STRING | 命名空间 |
| Item Path | `output_item_path` | STRING | 路径段 |
| Is Modded | `output_is_modded` | BOOLEAN | 是否非 `minecraft` 命名空间 |
| Valid | `output_valid` | BOOLEAN | 选择是否有效 |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| Selected Item | `minecraft:stone` | 当前选中 ID |
| Allow Modded Items | `true` | 是否在搜索中包含模组物品 |

### 选择器分类（UI）

All / Blocks / Tools / Food / Combat / Modded；快捷项含 stone、oak_planks、torch、bucket 等。

## 使用步骤

1. 打开选择器，按 Tools / Food 等过滤或搜索。
2. 把 **Item Type** 接到需要 `ITEM_TYPE` 的下游（注意：多数建造材质口要的是 **Block Type**，不要混接）。

## 典型接法

```text
[Item Type Selector].Item Type ──► [物品相关写入 / 查询节点]
```

## 注意事项

- **Item Type ≠ Block Type**：同名不一定互通（如部分方块物品）。砌墙 / 赋材质请用 Block Type Selector。
- 注册表未就绪时 Valid 可能为 false。
