# Batch 13.2 — 真机小建筑体验验收清单

> 目标：验证第一次使用 NodeCraft 的玩家，能否在 **5–10 分钟**内用建筑构件节点搭出像样的小建筑，并完成 **节点 → Preview → Apply**。
>
> 前置：Batch 13.1 Architectural Component Protocol 已冻结；本清单只验收 **Composition UX**，不再以“有没有新节点”为通过标准。

## 0. 环境门禁（开玩前）

- [ ] 本地 / CI：`build`、`test`、catalog generation 已通过（至少跑过与建筑相关的 contract）
- [ ] 客户端可进世界；NodeCraft 编辑器可打开
- [ ] 能看到 Architecture 预设分类，且包含：
  - [ ] `Mini Building (Component Chain)`
  - [ ] `Wall + Windows`
  - [ ] `Floor + Beam Grid`
  - [ ] `Roof + Eave Railing`
- [ ] 从任意建筑输出端口拖线时，推荐列表不是空的（规则 v2 已加载）

**门禁失败则不要开始体验验收。**

---

## 1. 通用体验准则（每栋建筑都要勾）

全程记录：墙钟时间、卡顿/崩溃、是否需要查文档。

### 1.1 搭图

- [ ] 5–10 分钟内完成“可预览的一栋小建筑”（不要求精美）
- [ ] 主链未被迫使用 God Node：
  - [ ] Floor 用 `Floor Slab`（± `Beam Grid`），不是只能用 `Floor Slab With Beams`
  - [ ] Roof 用 `Roof Base`（flat/shed/gable），不是只能用 `Roof Generator`
- [ ] 未出现 `ANY` 端口或“连不上只好改类型”的挫败
- [ ] PATH / BOX_FACE / FRAME_LIST / PATH_LIST 推荐连接至少帮过一次忙

### 1.2 Preview（PURE / PREVIEW_WRITE）

- [ ] `Preview Geometry` 能看见主体（楼板/墙/屋顶至少一类）
- [ ] 需要看路径时，`Preview Curves` 可用（Eave / Beam Center Lines）
- [ ] Preview **不写世界**（关掉预览后方块未永久改变）
- [ ] 改一个参数（厚度/高度/overhang）后预览能更新

### 1.3 Apply / Undo（WORLD_WRITE）

- [ ] `Voxelize Geometry` → `Preview Blocks`（可选）→ `Apply Changes` 成功写入
- [ ] Apply 使用真正的 **EXEC Trigger**（不是误点数据口）
- [ ] `Undo` 能撤掉本次建筑；`Redo` 能恢复
- [ ] Apply 后建筑在世界中位置/尺度合理（无明显飞天/沉地/碎成一条线）

### 1.4 失败即记（不阻塞整轮，但要记票）

遇到任一项打 ❌ 并写一句现象：

| 票 | 现象 | 严重度 (P0/P1/P2) |
|----|------|-------------------|
|  |  |  |

---

## 2. 五栋真机剧本（建议按顺序）

每栋：先用预设起步，再允许 1–2 处手工改线/改参。  
通过标准：**Preview 可读 + Apply 成功 + Undo 成功**。

### 剧本 A — Wall + Windows（开洞语义）

**预设：** `architectural.workflow.wall_with_windows`

- [ ] 载入后图可读：同一 `BOX_FACE` 同时喂给 Wall / Window Array
- [ ] Wall `output_geometry` → Difference `input_base`
- [ ] Wall `output_openings` → Difference `input_cutter`（洞口是刀具，不是“已开洞墙”）
- [ ] Window Array 有 `FRAME_LIST`；拖 Frames 时优先推荐 `Place Geometry On Frames`
- [ ] Preview 能区分：墙体 / 窗洞体 /（可选）窗框布置意图
- [ ] Apply 后墙上有洞口痕迹或可继续 Difference 烘焙结果合理
- [ ] **用时：** ____ 分钟

**刻意验证：** 不要把 `Composite(wall+openings)` 当成已经 boolean 完成。

---

### 剧本 B — Floor + Beam Grid（可组合楼面）

**预设：** `architectural.workflow.floor_with_beam_grid`

- [ ] 同一 Face → `Floor Slab` 与 `Beam Grid` 平行，不是绑死在 convenience 节点
- [ ] Beam Grid 输出 `FRAME_LIST` / `POINT_LIST` / `PATH_LIST`（Center Lines）
- [ ] Center Lines → `Preview Curves.input_paths` 可见多条梁线
- [ ] Combine 后 Preview Geometry 同时看到板 + 梁
- [ ] Apply + Undo 正常
- [ ] **用时：** ____ 分钟

**刻意验证：** 玩家能理解“先板后梁 / 或只要板”的拆分。

---

### 剧本 C — Roof + Eave（参考 PATH 驱动装饰）

**预设：** `architectural.workflow.roof_with_eave`

- [ ] `Roof Base` 用 gable（或 shed/flat）生成主体
- [ ] `output_eave_path` → Railing；拖线时 Railing / Beam Along Path 出现在推荐前列
- [ ] `Preview Curves` 能看到 eave 路径（即使当前是 primary edge 而非完整 loop）
- [ ] Combine：屋顶几何 + 栏杆几何 → Preview
- [ ] Apply + Undo 正常
- [ ] **用时：** ____ 分钟

**已知非阻塞：** Eave Path 目前可能是主檐边而非完整环线（P2 命名/环线问题）。  
若玩家误以为是闭合 gutter loop，记一票 P2，不判整轮失败。

---

### 剧本 D — Mini Building 全链（产品主路径）

**预设：** `architectural.residential.mini_building_v1`

- [ ] 链为：Floor Slab → Wall Along Path → Window Array → Roof Base → Combine → Voxelize → Preview
- [ ] Perimeter 来自 Face Boundary（POLYLINE → PATH 隐式可连）
- [ ] 墙沿真实路径走（矩形闭合至少 4 段），不是首尾弦线
- [ ] 屋顶是 `Roof Base`，不是 `Roof Generator`
- [ ] Preview Geometry + Preview Blocks 都可用
- [ ] Apply 后是“一栋可站立的小屋”，不是散落方块云
- [ ] Undo 清得干净（无残留幽灵块坑玩家）
- [ ] **用时：** ____ 分钟（目标 ≤ 10，含 Apply）

**这是本轮主验收剧本。** 其他剧本可减配，D 必须过。

---

### 剧本 E — 推荐连接空手搭（无预设，测 UX）

**禁止**一键载入 workflow 预设；只允许从空图或单个 Box 开始。

目标建筑（任选简化版）：

1. Box → Bottom Face → Floor Slab  
2. Face Boundary → Wall Along Path  
3. Front Face → Window Array  
4. Top Face → Roof Base  
5. Roof Eave → Railing（靠推荐点选，不靠记忆节点 id）  
6. Combine → Preview → Voxelize → Apply  

勾选：

- [ ] 全程主要靠端口拖线 + 推荐列表完成（搜索不超过 3 次）
- [ ] 推荐没有把玩家带去明显错误的节点家族（如随便塞进 Math/Flow）
- [ ] 仍能在 10 分钟内出 Preview；Apply 成功则加分
- [ ] **用时：** ____ 分钟  
- [ ] **搜索次数：** ____  

---

## 3. 协议抽查（每轮至少抽 3 项）

- [ ] Wall openings：Difference 切削语义仍成立  
- [ ] Window/Door/Column Grid：`FRAME_LIST` 可接到 Place On Frames  
- [ ] Beam Grid：`PATH_LIST` 可接到 Preview Curves `input_paths`  
- [ ] Railing：L 形 / 折线 PATH 不退化成首尾直线（可用 Face Boundary 闭合矩形验证）  
- [ ] Stair：仅声明“部分布局真跟随”；不要把 spiral/U 当成任意 PATH 楼梯验收失败理由  

---

## 4. 通过 / 不通过判定

### ✅ 通过（可收口 Batch 13.2 Composition UX）

同时满足：

1. 剧本 **D** 通过  
2. 剧本 **A / B / C** 至少通过 2 个  
3. 剧本 **E** 在 10 分钟内至少完成到 Preview（Apply 鼓励但不强制）  
4. 无 P0：崩溃、无法 Preview、无法 Apply、Undo 毁掉世界其他区域、建筑协议语义回退（如 openings 又被当成已开洞）

### 🟡 有条件通过

D 通过，但 A–C 只过 1 个，或 E 超时但可达 Preview；且仅有 P1/P2 票。  
→ 记下票后可冻结 UX，下一批修票。

### ❌ 不通过

任一项：

- D 失败  
- 出现 P0  
- 玩家必须依赖 God Node 才能搭完主链  
- Preview/Apply 主路径不可用  

---

## 5. 验收记录模板（复制填写）

```text
日期：
版本 / commit：
测试人：
客户端 / 世界：

门禁：通过 / 失败

剧本 A：通过 / 失败 / 跳过   用时：
剧本 B：通过 / 失败 / 跳过   用时：
剧本 C：通过 / 失败 / 跳过   用时：
剧本 D：通过 / 失败          用时：
剧本 E：通过 / 失败 / 部分    用时：  搜索次数：

P0：
P1：
P2：

总评：通过 / 有条件通过 / 不通过
下一批最值得修的一件事：
```

---

## 6. 本清单明确不做的事

- 不新增大量建筑节点  
- 不把 Roof Generator 扩成更多 roof type  
- 不引入 `BUILDING_HOST` / `ARCH_COMPONENT` 等重型类型  
- 不以“单元测试全绿”替代本清单（测试是门禁，体验是验收）

---

## 相关入口

| 类型 | 入口 |
|------|------|
| 语言冻结 | `docs/nodecraft-v1-node-language.md`（Batch 13.1 / 13.2 段） |
| 推荐规则 | `src/main/resources/nodecraft/node_recommendations.json` v2 |
| 预设 | `architectural.workflow.*`、`architectural.residential.mini_building_v1` |
| 合约 | `ArchitecturalMiniBuildingWorkflowContractTest`、`ArchitecturalRecommendationContractTest`、`ArchitecturalWorkflowPresetsContractTest` |
