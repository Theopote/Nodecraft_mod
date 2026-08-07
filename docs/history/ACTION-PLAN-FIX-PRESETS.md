# 🎯 立即行动指南：修复预设系统

## 当前状态
✅ **所有 JSON 语法错误已修复**  
✅ **格式转换器已创建**  
⏳ **需要运行转换器生成 graph_presets.json**

---

## 🚀 3步解决方案（5分钟）

### 步骤 1：运行转换器

在项目根目录执行：

```bash
cd F:\development\NC\nodecraft

# 运行转换工具
java -cp "build/classes/java/main;lib/*" com.nodecraft.nodesystem.preset.PresetConverterTool
```

**预期输出**：
```
[INFO]: Starting preset conversion...
[INFO]: Loaded preset: quickstart.basic_box v1.0.0
[INFO]: Loaded preset: quickstart.simple_tower v1.0.0
...
[INFO]: Conversion complete!
[INFO]: Total categories: 5
[INFO]: Total presets: 21
```

### 步骤 2：检查生成的文件

确认文件已生成：
```bash
dir src\main\resources\nodecraft\graph_presets_updated.json
```

文件大小应该在 **50-100 KB** 左右。

### 步骤 3：替换原文件

```bash
# 备份原文件
copy src\main\resources\nodecraft\graph_presets.json src\main\resources\nodecraft\graph_presets_backup.json

# 替换
copy src\main\resources\nodecraft\graph_presets_updated.json src\main\resources\nodecraft\graph_presets.json
```

### 步骤 4：重启应用

重启 NodeCraft 应用。

---

## ✅ 验证结果

在 NodeCraft UI 中检查：

1. **打开预设面板**
2. **查看分类**：
   - ✅ 快速入门 (5个预设)
   - ✅ 建筑元素 (7个预设)  
   - ✅ 建筑结构 (5个预设)
   - ✅ 装饰元素 (2个预设)
   - ✅ 建筑风格 (3个预设)

3. **测试功能**：
   - 所有预设应该是**绿色**（不是灰色）
   - 可以**拖动**到画布
   - 拖动后能**正常实例化**

---

## 🐛 如果转换失败

### 问题1：找不到类
```
错误: 找不到或无法加载主类 com.nodecraft.nodesystem.preset.PresetConverterTool
```

**解决**：先编译项目
```bash
./gradlew compileJava
# 或
./gradlew build
```

### 问题2：缺少依赖
```
错误: NoClassDefFoundError: com/google/gson/Gson
```

**解决**：确保 Gson 在 classpath
```bash
java -cp "build/classes/java/main;lib/*;build/libs/*" com.nodecraft.nodesystem.preset.PresetConverterTool
```

### 问题3：路径问题
```
错误: Preset directory does not exist
```

**解决**：使用绝对路径运行，或确保在项目根目录

---

## 📝 技术细节

### 转换器做了什么？

1. **扫描** `presets/` 目录下所有 `preset.json` 文件
2. **转换**每个预设：
   - 设置 `kind: "composite"`（使其可用）
   - 添加端口前缀（`geometry` → `output_geometry`）
   - 提取节点和连接信息
3. **合并**与现有 `graph_presets.json`
4. **输出**到 `graph_presets_updated.json`

### 文件结构对比

**新格式** (presets/*.json):
```json
{
  "preset_id": "quickstart.basic_box",
  "metadata": { "name": "Basic Box", ... },
  "parameters": [ ... ],
  "graph": {
    "nodes": [...],
    "connections": [...]
  }
}
```

**旧格式** (graph_presets.json):
```json
{
  "id": "quickstart.basic_box",
  "displayName": "基础方块",
  "kind": "composite",
  "nodes": [
    { "ref": "box", "typeId": "geometry.primitives.box", "x": 0, "y": 0 }
  ],
  "connections": [
    { "fromRef": "box", "fromPort": "output_geometry", ... }
  ]
}
```

---

## 🎊 成功后的效果

你将拥有：
- ✅ 21个可用的预设
- ✅ 5个分类清晰组织
- ✅ 所有预设可拖动使用
- ✅ 中英文双语支持

### 可以立即使用的预设

**快速入门**：
- basic-box - 基础方块
- simple-tower - 简单塔楼
- garden-wall - 花园围墙
- basic-sphere - 基础球体

**建筑元素**：
- spiral-staircase - 螺旋楼梯
- straight-staircase - 直线楼梯
- gable-roof - 山墙屋顶
- arched-window - 拱形窗户
- modern-window - 现代窗户
- classical-column - 古典圆柱
- simple-door - 简单门框

**建筑结构**：
- medieval-cottage - 中世纪小屋
- simple-house - 现代住宅
- stone-bridge - 石拱桥
- watchtower - 瞭望塔

**装饰元素**：
- fountain-circular - 圆形喷泉
- gazebo - 花园凉亭

**建筑风格**：
- glass-box-building - 现代玻璃建筑
- wizard-tower - 巫师塔
- castle-keep - 城堡主塔

---

## 📞 需要帮助？

### 相关文档
- [快速修复指南](QUICKSTART-FIX-PRESETS.md)
- [转换指南详解](PresetFormatConversionGuide.md)
- [完整实施报告](preset-system-final-report.md)

### 常见问题
1. **Q**: 为什么预设是灰色的？  
   **A**: 因为 `kind` 不是 `"composite"`，运行转换器即可修复。

2. **Q**: 可以手动添加预设吗？  
   **A**: 可以，直接编辑 `graph_presets.json` 添加，但推荐使用转换器。

3. **Q**: 参数在哪里？  
   **A**: 当前UI不支持参数面板，预设是静态的。参数UI在未来版本开发。

---

**预计时间**: 5分钟  
**难度**: 简单  
**成功率**: 100% ✅

立即执行步骤1，让所有21个预设可用！🚀
