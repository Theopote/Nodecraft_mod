# Geometry Boolean 工作流指南

面向 Minecraft 方块建模的 `geometry.boolean` 分类使用说明。节点库中的排序为：**0–4 体素 CSG / 包围盒**，**10–23 SDF**。

## 体素 CSG（建筑构件首选）

用于墙体、塔楼、门窗洞口等**方块级**造型。结果经 `GeometryVoxelizer` 体素化后写入世界。

### 1. 合并多个部件

```text
Box Center Size (墙) ──┐
Box Center Size (耳堂) ──┼→ Combine Geometry → Assign Block Type → Apply Changes
Prism … (屋顶)      ──┘
```

- **Combine Geometry**（`geometry.boolean.union`）：合并多路几何，烘焙时为**体素并集**。
- 不是 SDF 的 smooth union；有机圆角请走下方 SDF 链。

### 2. 挖洞 / 开窗（哥特教堂、墙体开口）

```text
Box Center Size (外墙) ──→ Base ──┐
Box Center Size (窗洞) ──→ Cutter ─┼→ Difference → Assign Block Type → Apply Changes
```

- **Difference**：基底减去刀具，刀具区域强制按实心体素切除。
- **Geometry Bounds** 对 Difference 输出会使用 **基底 ∪ 刀具** 的包围盒，避免刀具超出墙体时范围偏小。

### 3. 取交集（掩膜、局部保留）

```text
Geometry A ──→ Left  ──┐
Geometry B ──→ Right ──┼→ Intersection → …
```

### 4. 定范围

| 场景 | 节点 |
|------|------|
| 已有方块选区 / 坐标列表 | Bounding Box |
| 任意 GeometryData | Geometry Bounds |

输出 Region / Min-Max / Volume，可接填充、预览或世界写入节点。

---

## SDF 链（有机造型 / 圆角融合）

用于圆角布尔、噪声表皮等；需经 **SDF To Geometry** 再进入几何体素管线。

### 推荐链

```text
SDF Box / SDF Sphere
    → SDF Boolean（Operation: DIFFERENCE / UNION，Smooth K 控制圆角）
    → SDF To Geometry（Auto Bounds 开，Bounds Padding ≈ 1）
    → Geometry Viewer（预览）
    → Assign Block Type → Apply Changes
```

### SDF To Geometry 要点

| 属性 / 端口 | 说明 |
|-------------|------|
| **Auto Bounds**（默认开） | 未接 Min/Max 时自动估算 SDF 包围盒 |
| **Bounds Padding** | 自动包围盒外扩格数，默认 1 |
| Bounds Min / Max | 手动覆盖自动范围 |
| Padding（输入） | 运行时覆盖边距 |

### 性能限制

- 体素化体积上限约 **262144** 格（64³）。
- 超出时日志警告并跳过烘焙；请缩小范围或改用 Box/Prism + Difference。

### 已弃用

- **SDF Smooth Boolean (Legacy)**：请改用 **SDF Boolean**，将 **Smooth K** 设为大于 0。

---

## 与 `geometry.primitives` 如何选型

| 需求 | 推荐 |
|------|------|
| 直墙、方盒、棱柱、环形体 | `geometry.primitives` / `geometry.solids` |
| 多部件合并 | Combine Geometry |
| 规则开洞 | Difference |
| 圆角/平滑布尔/噪声外形 | SDF 链 |
| 只要外轮廓尺寸 | Geometry Bounds |

---

## 参考：哥特教堂片段

见 [simple-gothic-node-audit.md](simple-gothic-node-audit.md)：外壳与洞口使用 **Difference**，部件合并使用 **Combine Geometry**（原 Geometry Union）。
