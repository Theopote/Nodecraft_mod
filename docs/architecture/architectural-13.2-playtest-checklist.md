# Batch 13.2 — 真机小建筑体验验收清单

> **状态（仓库验收 2026-09-23）**  
> - Composition UX **代码 / 门禁**：通过（PATH_LIST、推荐 v2、Workflow Presets、合约、本清单）  
> - Composition UX **产品体验**：⏳ 待 Minecraft 真机；**不得**仅凭 contract 勾绿  
>
> **目标 KPI：** 第一次使用 NodeCraft 的玩家，能否在 **5–10 分钟**内搭出可预览小建筑，并完成 **节点 → Preview → Apply**。  
> **冻结范围：** Batch 13.1 Protocol + 13.2 UX infrastructure。**现在不要**继续加建筑节点，也**不要**开 Batch 13.3 大开发——先跑完本清单。

## 0. 环境门禁（开玩前）

- [ ] 本地 / CI：`build`、`test`、catalog generation 已通过（至少建筑相关 contract）
- [ ] 客户端可进世界；NodeCraft 编辑器可打开
- [ ] Architecture 预设可见：
  - [ ] `Mini Building (Component Chain)`
  - [ ] `Wall + Windows`
  - [ ] `Floor + Beam Grid`
  - [ ] `Roof + Eave Railing`
- [ ] 从建筑输出端口拖线时推荐列表非空（规则 v2）

**门禁失败则不要开始体验验收。**

---

## 建议真机顺序

| 顺序 | 剧本 | 测什么 |
|------|------|--------|
| 1 | **D** Mini Building | 系统能不能工作（有 preset） |
| 2 | **E** 空白画布 + 推荐 | 用户能不能理解系统（无 preset）— **最关键 UX** |
| 3 | A Wall + Windows | Opening / Difference 语义 |
| 4 | B Floor + Beam Grid | 可组合楼面 |
| 5 | C Roof + Eave | 参考 PATH 驱动装饰 |

记录重点不是“有没有报错”，而是：

完成时间 · 搜索次数 · 错误连接次数 · Preview 等待 · Apply 等待 · 查文档次数 · 视觉问题

---

## 1. 通用体验准则（每栋建筑都要勾）

### 1.1 搭图

- [ ] 5–10 分钟内完成“可预览的一栋小建筑”（不要求精美）
- [ ] 主链未被迫使用 God Node：
  - [ ] Floor → `Floor Slab`（± `Beam Grid`）
  - [ ] Roof → `Roof Base`（flat/shed/gable）
- [ ] 未出现 `ANY` 或“连不上只好改类型”
- [ ] PATH / BOX_FACE / FRAME_LIST / PATH_LIST 推荐至少帮过一次

### 1.2 Preview（PURE / PREVIEW_WRITE）

- [ ] `Preview Geometry` 能看见主体
- [ ] 需要时 `Preview Curves` 可用（Eave / Beam Center Lines）
- [ ] Preview **不写世界**
- [ ] 改参后预览能更新
- [ ] **Preview 等待：** ____ s（主观可接受 / 卡顿）

### 1.3 Apply / Undo（WORLD_WRITE）

- [ ] Voxelize →（可选 Preview Blocks）→ Apply Changes 成功
- [ ] Apply 使用真正的 **EXEC Trigger**
- [ ] Undo 撤掉本次建筑；Redo 能恢复
- [ ] **Apply 等待：** ____ s
- [ ] Undo 后无残留幽灵块 / 不毁掉无关区域

### 1.4 失败即记

| 票 | 现象 | P0/P1/P2 |
|----|------|----------|
|  |  |  |

---

## 2. 五栋真机剧本

### 剧本 D — Mini Building 全链（系统主路径）

**预设：** `architectural.residential.mini_building_v1`  
**测：** 系统能不能工作。

- [ ] 链：Floor Slab → Wall Along Path → Window Array → Roof Base → Combine → Voxelize → Preview
- [ ] Perimeter：Face Boundary（POLYLINE → PATH）
- [ ] 墙沿真实路径（闭合矩形 ≥ 4 段），非首尾弦线
- [ ] 屋顶是 `Roof Base`，不是 `Roof Generator`
- [ ] Preview Geometry + Preview Blocks 可用
- [ ] Apply 后是可站立小屋，不是散落方块云
- [ ] Undo 干净

**视觉抽查（contract 测不到，必须真机看）：**

- [ ] 墙角无明显断裂 / 双厚重叠
- [ ] 屋顶与墙顶无明显悬空或穿插错层
- [ ] 楼板与墙的高度关系自然（无整层漂浮 / 埋半格）

- [ ] **用时：** ____ 分钟（目标 ≤ 10，含 Apply）  
- [ ] **Preview 等待 / Apply 等待：** ____ / ____ s

**D 必须过**，否则整轮不通过。

---

### 剧本 E — 空白画布 + 推荐（UX 主路径）

**禁止**一键载入 workflow 预设；只允许空图或单个 Box。  
**测：** 用户能不能理解系统。**本轮最值得认真测。**

目标链（靠拖线 + 推荐，不靠记 id）：

1. Box → Bottom Face → Floor Slab  
2. Face Boundary → Wall Along Path  
3. Front Face → Window Array  
4. Top Face → Roof Base  
5. Roof Eave → Railing（推荐点选）  
6. Combine → Preview →（可选）Voxelize → Apply  

理想推荐形态示例：

```text
Top Face ──drag──► Recommended
                   ★ Roof Base
                     Floor Slab
                     Column Grid
                     …
```

勾选：

- [ ] 主要靠端口拖线 + 推荐完成（**搜索 ≤ 3 次**）
- [ ] 推荐未把玩家带去明显错误家族（Math / Flow 等）
- [ ] 关键正确节点出现在**推荐前列**（主观：前 5 内能点到；若总在第 10+ 记 P1）
- [ ] 10 分钟内至少到 Preview；Apply 成功加分
- [ ] **用时：** ____ 分钟  
- [ ] **搜索次数：** ____  
- [ ] **错误连接次数（连错再拆）：** ____  
- [ ] **查文档次数：** ____  
- [ ] **Preview / Apply 等待：** ____ / ____ s

---

### 剧本 A — Wall + Windows（开洞语义）

**预设：** `architectural.workflow.wall_with_windows`

- [ ] 同一 `BOX_FACE` → Wall / Window Array
- [ ] Wall geometry → Difference `input_base`；openings → `input_cutter`
- [ ] Window `FRAME_LIST` 拖线优先 Place On Frames
- [ ] Preview / Apply 合理
- [ ] **用时：** ____ 分钟

**刻意验证：** openings 是刀具，不是“已开洞墙”。

---

### 剧本 B — Floor + Beam Grid

**预设：** `architectural.workflow.floor_with_beam_grid`

- [ ] Face 并行 → Floor Slab + Beam Grid（非 convenience 绑死）
- [ ] Center Lines：`PATH_LIST` → Preview Curves `input_paths`
- [ ] Combine Preview 可见板 + 梁；Apply + Undo 正常
- [ ] **用时：** ____ 分钟

---

### 剧本 C — Roof + Eave

**预设：** `architectural.workflow.roof_with_eave`

- [ ] Roof Base → Eave Path → Railing；推荐前列含 Railing / Beam Along Path
- [ ] Preview Curves 可见 eave；Combine + Apply 正常
- [ ] **用时：** ____ 分钟

**已知 P2（不阻塞）：** Eave Path 可能是主檐边而非完整 loop。  
误以为闭合 gutter 时记 P2，不判失败。  
（未来可升级 `eave_paths` / `ridge_paths` : `PATH_LIST`——等有第二个 multi-eave 消费者再做。）

---

## 3. 协议抽查（每轮至少 3 项）

- [ ] Wall openings → Difference 切削语义  
- [ ] Window/Door/Column Grid → `FRAME_LIST` → Place On Frames  
- [ ] Beam Grid → `PATH_LIST` → Preview Curves  
- [ ] Railing：折线 PATH 不退化成首尾直线  
- [ ] Stair：仅“部分布局真跟随”，不按任意 PATH 楼梯苛责  

---

## 4. 通过 / 不通过判定

### ✅ 通过（可正式冻结 Architectural Composition UX v1）

1. **D** 通过（含视觉抽查）  
2. **E** 在 10 分钟内至少到 Preview，且搜索 ≤ 3、无 P0  
3. **A / B / C** 至少通过 2 个  
4. 无 P0（崩溃、无法 Preview/Apply、Undo 毁无关区域、opening 语义回退）

通过后：**停止围着基础协议打转**；下一批转向建筑编辑效率 / 参数交互 / Preview 反馈（不是继续堆建筑节点）。

### 🟡 有条件通过

D 通过；E 超时但可达 Preview，或 A–C 只过 1 个；仅有 P1/P2。  
→ 记票后可冻结 UX infrastructure，修票再开效率向批次。

### ❌ 不通过

- D 失败，或出现 P0  
- 必须依赖 God Node 才能搭完主链  
- Preview/Apply 主路径不可用  
- E 几乎无法靠推荐完成（必须大量搜索 / 查文档）  

---

## 5. 验收记录模板

```text
日期：
版本 / commit：
测试人：
客户端 / 世界：

门禁：通过 / 失败

顺序实际：D → E → … 

剧本 D：通过 / 失败   用时：   Preview等待：   Apply等待：
  墙角：OK/问题   屋顶墙顶：OK/问题   楼板墙高：OK/问题

剧本 E：通过 / 失败 / 部分   用时：   搜索：   错连：   查文档：
  Preview等待：   Apply等待：   推荐是否在前列：是/否

剧本 A：通过 / 失败 / 跳过   用时：
剧本 B：通过 / 失败 / 跳过   用时：
剧本 C：通过 / 失败 / 跳过   用时：

P0：
P1：
P2：

总评：通过 / 有条件通过 / 不通过
下一批最值得修的一件事：
```

---

## 6. 明确不做 / 延后

**现在不做**

- 不新增大量建筑节点  
- 不扩 Roof Generator 类型  
- 不引入 `BUILDING_HOST` / `ARCH_COMPONENT`  
- 不以单元测试替代真机勾选  
- 不因 Eave Path 环线问题重开 Roof 架构  

**延后（建议 Batch 13.3 或更后，真机跑完再开）**

- Recommendation **Ranking** Contract：不仅 `contains`，还要锁定 Top-N 顺序（如 Eave → 1.Railing 2.Beam Along Path 3.Preview Curves）  
- Roof `eave_paths` / `ridge_paths` : `PATH_LIST`（等 multi-eave 真消费者）  
- 建筑编辑效率 / 参数交互 / Preview 反馈（Composition UX v1 冻结之后）

---

## 相关入口

| 类型 | 入口 |
|------|------|
| 语言冻结 | `docs/nodecraft-v1-node-language.md` |
| 推荐规则 | `node_recommendations.json` v2 |
| 预设 | `architectural.workflow.*`、`mini_building_v1` |
| 合约门禁 | MiniBuilding / Recommendation / WorkflowPresets ContractTest |
