# Preset Library v2 — Runtime Acceptance 记录表

> **状态：** ⏳ 待 Minecraft 真机  
> **目标：** 在静态 contract（拓扑 + 坐标空间）已通过的前提下，确认 6 个代表 preset 在客户端中 **Geometry Preview ≡ Block Preview ≡ Apply 结果**（空间与形状一致）。  
> **冻结范围：** Preset Library v2 最后一轮验收；通过后停止大规模改 preset JSON。  
> **关联静态测试：** `PresetSemanticAuditTest`、`PresetCoordinateSpaceAuditTest`、`GraphPresetResourceTest`

---

## 0. 环境门禁（开测前）

| # | 检查项 | Pass | 备注 |
|---|--------|------|------|
| 0.1 | 本地 `./gradlew test` 通过（至少上述 3 个 preset 测试类） | ☐ | |
| 0.2 | 客户端可进创造模式平坦世界 | ☐ | 建议超平坦，Y=64，白天 |
| 0.3 | NodeCraft 编辑器可打开，内置 Preset 面板可见 | ☐ | |
| 0.4 | 测试者站在 **空旷区域**（周围 32×32 无遮挡） | ☐ | 便于观察 preview 与 apply |
| 0.5 | 记录：MC 版本 ____ · NodeCraft 构建/commit ____ · 测试者 ____ · 日期 ____ | | |

**门禁任一失败 → 不要开始 Runtime Acceptance。**

---

## 1. 通用验收流程（每个 preset 重复）

对每个 preset 按顺序执行并勾选：

```
加载预设
  → [A] 图完整（节点 + 连线无缺失）
  → [B] Geometry Preview 可见且形状合理
  → [C] Block Preview 可见
  → [D] Geometry 与 Block Preview 空间重合（同一位置、同一外轮廓）
  → [E] 改参后两种 Preview 同步更新
  → [F] Apply（若 preset 含 Apply Changes）
  → [G] Apply 后实体方块与 Preview 一致
  → [H] Undo 撤掉 Apply（Mini Building 必测）
```

### 1.1 空间重合判定（D / G）

站在建筑 **侧面与顶面** 各看一次：

| 现象 | 判定 |
|------|------|
| Ghost geometry 与 ghost blocks **完全重叠** | ✅ Pass |
| 两者形状对但 **整体平移**（例如相差一个 player 位置） | ❌ Fail — 坐标链 |
| 只有一边有 preview / 一边空白 | ❌ Fail — 链断裂 |
| Geometry 在原点、Blocks 在玩家附近（或反之） | ❌ Fail — split chain |

### 1.2 参数同步判定（E）

改一个 **显眼参数**（见各 preset 剧本），确认：

- Geometry Preview **立即或数秒内**更新  
- Block Preview **同步**更新（不能只有一边变）  
- 改回默认值后恢复  

### 1.3 失败记录（每 preset 最多 1 行）

| Preset | 失败步骤 | 现象 | P0/P1/P2 | Issue # |
|--------|----------|------|---------|---------|
| | | | | |

---

## 2. 六案例验收剧本（建议顺序）

| 顺序 | Preset ID | 显示名 | 坐标模式 | 含 Apply |
|------|-----------|--------|----------|----------|
| 1 | `quickstart.basic_box` | Basic Box | World-space 教学 | 否 |
| 2 | `building_elements.columns.classical_column` | Classical Column | Local → Move | 否 |
| 3 | `building_elements.stairs.spiral_staircase` | Spiral Staircase | Local → Move | 否 |
| 4 | `decorative.fountain_circular` | Circular Fountain | Local → Move | 否 |
| 5 | `styles.modern.glass_box_building` | Modern Glass Box Building | Local → Move（双材质） | 否 |
| 6 | `architectural.residential.mini_building_v1` | Mini Building | **Local @ 原点（无 Move）** | **是** |

---

### 案例 1 — Basic Box（World-space baseline）

**Preset ID：** `quickstart.basic_box`  
**测什么：** 最简单教学链；Player Position 直接驱动几何中心。

**主链：**
```text
Player Position → Box → Preview Geometry
                      └→ Voxelize → Assign Block Type → Preview Blocks
```

**加载后预期：**
- 5×5×5 方块以 **玩家位置为中心** 出现  
- 无 Move Geometry 节点  

**操作步骤：**

| 步骤 | 操作 | Pass |
|------|------|------|
| 1 | 加载 preset，执行图 | ☐ |
| 2 | 确认 6 个节点、连线完整 | ☐ |
| 3 | 开启 Geometry Preview — 看到线框/ghost 立方体 | ☐ |
| 4 | 开启 Block Preview — 看到 ghost 方块 | ☐ |
| 5 | **D** 两种 preview 重合，中心在玩家附近 | ☐ |
| 6 | **E** 选中 Box 节点，将 `sizeX` 改为 `7.0` | ☐ |
| 7 | 两种 preview 同步变宽 | ☐ |
| 8 | 改回 `5.0` | ☐ |

**失败判据：** Preview 出现在世界原点而非玩家附近；Geometry 与 Blocks 不重合。

**用时：** ____ min · **Preview 延迟：** ____ s

**总评：** ☐ Pass · ☐ Fail · **记录人：** ____

---

### 案例 2 — Classical Column（Local → Move，防 double translation）

**Preset ID：** `building_elements.columns.classical_column`  
**测什么：** 柱子在局部堆叠，仅一次 Move 到玩家位置。

**主链：**
```text
World Frame (local_origin) → Base → Shaft → Capital → Combine
                                                      ↓
Player Position → Move Geometry → Preview / Voxelize / Material
```

**加载后预期：**
- 经典柱（底座 + 柱身 + 柱头）整体出现在 **玩家脚边**  
- **不是** 偏移 2× player 距离的“幽灵柱”  

**操作步骤：**

| 步骤 | 操作 | Pass |
|------|------|------|
| 1 | 加载 preset，执行图 | ☐ |
| 2 | 确认含 `local_origin`（World Frame）与 `move_to_pos` | ☐ |
| 3 | Geometry Preview — 完整圆柱/ frustum 柱 | ☐ |
| 4 | Block Preview 与 Geometry 重合 | ☐ |
| 5 | **D** 柱 **底面** 接近玩家站立高度（允许 ±1 格误差） | ☐ |
| 6 | **E** 将 `shaft_height` 从 `3.0` 改为 `5.0` | ☐ |
| 7 | 柱身变高，柱头仍在上端，两种 preview 同步 | ☐ |
| 8 | 改回 `3.0` | ☐ |

**失败判据：**
- 柱子在远处或 `(0,0,0)` 附近，与玩家分离  
- 柱底明显高于/低于玩家脚面一个完整 player 偏移（疑似 double translation）  

**用时：** ____ min

**总评：** ☐ Pass · ☐ Fail · **记录人：** ____

---

### 案例 3 — Spiral Staircase（Local path + center post）

**Preset ID：** `building_elements.stairs.spiral_staircase`  
**测什么：** 螺旋楼梯 + 中心柱局部建模，整体 Move 一次。

**主链：**
```text
local_origin → Path → Staircase ─┐
Center Post (local cylinder) ─────┼→ Combine → Move ← Player Position
```

**加载后预期：**
- 螺旋楼梯绕中心柱上升，整体在玩家附近  
- 中心柱与楼梯 **同轴**  

**操作步骤：**

| 步骤 | 操作 | Pass |
|------|------|------|
| 1 | 加载 preset，执行图 | ☐ |
| 2 | Geometry Preview — 可见螺旋踏步 + 中心柱 | ☐ |
| 3 | Block Preview 与 Geometry 重合 | ☐ |
| 4 | **D** 楼梯 **底部** 在玩家附近（非原点） | ☐ |
| 5 | **E** 将 `spiral_turns` 从 `1.5` 改为 `2.0` | ☐ |
| 6 | 螺旋圈数增加，两种 preview 同步 | ☐ |
| 7 | 改回 `1.5` | ☐ |

**失败判据：** 楼梯在原点而玩家在别处；中心柱与楼梯错位；double translation 式远距偏移。

**用时：** ____ min

**总评：** ☐ Pass · ☐ Fail · **记录人：** ____

---

### 案例 4 — Circular Fountain（Multi-cylinder composite）

**Preset ID：** `decorative.fountain_circular`  
**测什么：** 多层 cylinder + boolean 组合，无 per-primitive player 锚点。

**主链：**
```text
Basin (outer − inner) + Tier + Spout → Combine → Move ← Player Position
```

**加载后预期：**
- 圆形水池 + 内层 tier + 中心喷口 **一体** 出现在玩家附近  
- 各层 **同心**，无“层与层整体平移分离”  

**操作步骤：**

| 步骤 | 操作 | Pass |
|------|------|------|
| 1 | 加载 preset，执行图 | ☐ |
| 2 | Geometry Preview — 可见外池、内空、中层、喷口 | ☐ |
| 3 | Block Preview 与 Geometry 重合 | ☐ |
| 4 | **D** 喷泉 **底面** 在玩家附近地面 | ☐ |
| 5 | **E** 选中 `center_spout`，将 `radius` 从 `0.4` 改为 `0.8` | ☐ |
| 6 | 喷口变粗，两种 preview 同步 | ☐ |
| 7 | 改回 `0.4` | ☐ |

**失败判据：** 池体与喷口分离；整体出现在原点；Geometry/Blocks 不重合。

**用时：** ____ min

**总评：** ☐ Pass · ☐ Fail · **记录人：** ____

---

### 案例 5 — Glass Box Building（Dual material + split chain 回归）

**Preset ID：** `styles.modern.glass_box_building`  
**测什么：** 双材质 placement 链与 geometry preview 使用同一 world-space 语义。

**主链：**
```text
Glass Shell ─→ move_glass ─→ Voxelize ─→ Material Glass ─┐
Frame Grid  ─→ move_frame ─→ Voxelize ─→ Material Frame ─┼→ Merge → Preview Blocks
Glass + Frame → Combine → move_to_pos → Preview Geometry ┘
                              ↑
                       Player Position (×3)
```

**加载后预期：**
- 玻璃外壳 + 内部框架柱 **同时** 出现在玩家附近  
- Geometry ghost 与 glass/frame block ghost **空间重合**（不是 geometry 在玩家处、blocks 在原点）  

**操作步骤：**

| 步骤 | 操作 | Pass |
|------|------|------|
| 1 | 加载 preset，执行图 | ☐ |
| 2 | 确认含 `move_glass`、`move_frame`、`move_to_pos` | ☐ |
| 3 | Geometry Preview — 玻璃盒 + 框架可见 | ☐ |
| 4 | Block Preview — 浅蓝玻璃 + 铁块框架 ghost | ☐ |
| 5 | **D** 两种 preview **外轮廓重合**（重点：四角框架与玻璃边对齐） | ☐ |
| 6 | **E** 将 `frame_rows` 从 `5` 改为 `3` | ☐ |
| 7 | 框架行数减少，geometry 与 blocks 同步 | ☐ |
| 8 | 改回 `5` | ☐ |

**失败判据：**
- Geometry preview 在玩家附近，block preview 在世界原点（或反之）— **split chain 回归**  
- 只有玻璃无框架，或只有框架无玻璃  

**用时：** ____ min

**总评：** ☐ Pass · ☐ Fail · **记录人：** ____

---

### 案例 6 — Mini Building（完整 Apply 链）

**Preset ID：** `architectural.residential.mini_building_v1`  
**测什么：** 建筑组件链 + Preview Blocks + **Apply Changes** + Undo。

> **注意（当前 preset 设计）：** 此 preset **不含** Player Position / Move Geometry。  
> 预览与 Apply 默认出现在 **世界原点附近**，而非玩家脚边。  
> 本案例重点验收 **Apply 链与 preview 一致性**，不是“建筑跟随玩家”。

**主链：**
```text
Volume → Floor / Walls / Windows / Roof → Combine
  ├→ Preview Geometry
  └→ Voxelize → Material → Preview Blocks
                           └→ Apply Changes
```

**操作步骤：**

| 步骤 | 操作 | Pass |
|------|------|------|
| 1 | 加载 preset；**先走到离原点较远处**（便于区分） | ☐ |
| 2 | 执行图 — Geometry + Block Preview 出现在 **(0,0,0) 附近** | ☐ |
| 3 | **D** Geometry 与 Block Preview 重合（小建筑轮廓：墙+窗+顶） | ☐ |
| 4 | **E** 选中 volume，将 `sizeX` 从 `10` 改为 `12` | ☐ |
| 5 | 建筑变宽，两种 preview 同步 | ☐ |
| 6 | **F** 触发 Apply Changes（EXEC） | ☐ |
| 7 | **G** 真实方块与 Apply 前 Block Preview **一致** | ☐ |
| 8 | **H** Undo — 方块全部撤销，无残留 ghost/实体 | ☐ |
| 9 | （可选）Redo — 恢复 Apply 结果 | ☐ |

**失败判据：**
- Apply 后方块云散落 / 与 preview 不符  
- Undo 后残留方块  
- Geometry Preview 与 Block Preview 位置或形状不一致  

**视觉抽查：**
- ☐ 墙角无严重断裂  
- ☐ 屋顶与墙顶衔接自然  
- ☐ 窗户区域有开洞感（非实心墙）  

**用时：** ____ min · **Apply 延迟：** ____ s · **Undo 干净：** ☐

**总评：** ☐ Pass · ☐ Fail · **记录人：** ____

---

## 3. 总验收记分表

| # | Preset | A 图完整 | B Geo | C Blocks | D 重合 | E 改参 | F Apply | G 一致 | H Undo | 总评 |
|---|--------|----------|-------|----------|--------|--------|---------|--------|--------|------|
| 1 | Basic Box | ☐ | ☐ | ☐ | ☐ | ☐ | N/A | N/A | N/A | ☐ |
| 2 | Classical Column | ☐ | ☐ | ☐ | ☐ | ☐ | N/A | N/A | N/A | ☐ |
| 3 | Spiral Staircase | ☐ | ☐ | ☐ | ☐ | ☐ | N/A | N/A | N/A | ☐ |
| 4 | Fountain | ☐ | ☐ | ☐ | ☐ | ☐ | N/A | N/A | N/A | ☐ |
| 5 | Glass Box | ☐ | ☐ | ☐ | ☐ | ☐ | N/A | N/A | N/A | ☐ |
| 6 | Mini Building | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

**通过标准：**
- 案例 1–5：**D + E** 必须全部 Pass（B/C 为 D 的前提）  
- 案例 6：**D + E + F + G + H** 必须全部 Pass  
- **任意 P0 失败 → Preset Library v2 不得冻结**

---

## 4. 冻结判定

当且仅当以下条件 **全部满足**，可将 Preset Library v2 标记为 **Runtime Accepted**：

- [ ] 本节 6 案例总评均为 Pass  
- [ ] 无未关闭的 P0 坐标/预览/Apply 问题  
- [ ] P1 问题已有 ticket 且不影响冻结（可选）  
- [ ] 验收记录已归档（commit 或 wiki 链接）：________________  

**冻结后建议：** 停止 preset JSON 大改；后续 preset 改动走增量 + 静态 audit 回归。

---

## 5. 已知静态 contract 与真机差异（测前阅读）

| 项 | 静态 audit 保证 | 真机仍需验证 |
|----|-----------------|--------------|
| 拓扑 / 端口 / 类型 | ✅ CI | 加载器无 silent drop |
| Material / Voxelize 消费 | ✅ CI | 材质视觉正确 |
| Double translation | ✅ CI | 玩家附近位置感 |
| Split preview chain | ✅ CI | Ghost 重合 |
| Apply ↔ Preview | ☐ 部分 CI | 实体方块一致 |
| Undo / Redo | ☐ | 仅 Mini Building 必测 |
| Mini Building @ 原点 | 设计如此 | 确认团队接受或后续加 Move |

---

## 6. 附录 — 快速定位 preset

| 显示名 | Preset ID | 分类 |
|--------|-----------|------|
| Basic Box | `quickstart.basic_box` | Quickstart |
| Classical Column | `building_elements.columns.classical_column` | Building Elements |
| Spiral Staircase | `building_elements.stairs.spiral_staircase` | Building Elements |
| Circular Fountain | `decorative.fountain_circular` | Decorative |
| Modern Glass Box Building | `styles.modern.glass_box_building` | Styles |
| Mini Building (Component Chain) | `architectural.residential.mini_building_v1` | Architecture |
