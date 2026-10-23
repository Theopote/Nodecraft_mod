# Block Type Selector

| 字段 | 值 |
|---|---|
| 节点 ID | `input.type_selectors.block_type_selector` |
| 分类 | `input.type_selectors` |
| 显示名称 | Block Type Selector |
| 效果 | `PURE` |

## 作用

搜索并选定一个 Minecraft **方块类型** ID，输出给材质赋值、调色板、世界写入等节点。默认 `minecraft:stone`。

## 端点

### 输出

| 显示名 | 端口 ID | 类型 | 说明 |
|---|---|---|---|
| Block Type | `output_block_id` | BLOCK_TYPE | 完整 ID，如 `minecraft:oak_planks` |
| Namespace | `output_namespace` | STRING | 命名空间，如 `minecraft` |
| Block Path | `output_block_path` | STRING | 路径段，如 `oak_planks` |
| Is Modded | `output_is_modded` | BOOLEAN | 是否非 `minecraft` 命名空间 |
| Valid | `output_valid` | BOOLEAN | 选择是否有效（注册表就绪且 ID 可用） |

### 属性

| 属性 | 默认 | 说明 |
|---|---|---|
| Selected Block | `minecraft:stone` | 当前选中 ID |
| Allow Modded Blocks | `true` | 搜索结果是否包含模组方块 |

### 选择器分类（UI）

All / Stone / Wood / Natural / Decor / Redstone / Functional / Nether·End / Modded；另有快捷项（stone、cobblestone、glass 等）。

## 使用步骤

1. 拖入节点，点开选择器（或改 Selected Block 属性）。
2. 用搜索或分类找到目标方块。
3. 把 **Block Type** 接到 `Assign Block Type`、调色板、写入类节点。

## 典型接法

```text
[Block Type Selector].Block Type ──► [Assign Block Type].Block
[Block Type Selector].Block Type ──► [Create Block Palette] 的方块列表项
```

## 注意事项

- 注册表未就绪时 Valid 可能为 false（刚进世界前偶发），看节点状态后再跑图。
- **Block Type** 是类型 ID，不等于带 state 的完整方块实例；朝向 / 半砖等用 `material.block_state` 系列。
- 关掉 Allow Modded 后，模组 ID 不会出现在搜索里；已选模组 ID 的行为以 Valid / 输出为准。
