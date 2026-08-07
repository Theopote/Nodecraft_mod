# 批量修复预设节点ID - 完整方案

## 需要修复的文件（20个）

1. ✅ quickstart/basic-box/preset.json - **已修复**
2. ⏳ quickstart/basic-sphere/preset.json
3. ⏳ quickstart/garden-wall/preset.json
4. ⏳ quickstart/simple-tower/preset.json
5. ⏳ building-elements/stairs/spiral-staircase/preset.json
6. ⏳ building-elements/stairs/straight-staircase/preset.json
7. ⏳ building-elements/roofs/gable-roof/preset.json
8. ⏳ building-elements/windows/arched-window/preset.json
9. ⏳ building-elements/windows/modern-window/preset.json
10. ⏳ building-elements/columns/classical-column/preset.json
11. ⏳ building-elements/doors/simple-door/preset.json
12. ⏳ architectural/residential/medieval-cottage/preset.json
13. ⏳ architectural/residential/simple-house/preset.json
14. ⏳ architectural/infrastructure/stone-bridge/preset.json
15. ⏳ architectural/infrastructure/watchtower/preset.json
16. ⏳ decorative/fountain-circular/preset.json
17. ⏳ decorative/gazebo/preset.json
18. ⏳ styles/modern/glass-box-building/preset.json
19. ⏳ styles/fantasy/wizard-tower/preset.json
20. ⏳ styles/medieval/castle-keep/preset.json

## 需要执行的替换操作

### 全局搜索替换（所有文件）

```
"type": "geometry.primitives.box_by_corner_and_size"
→ "type": "geometry.primitives.box_from_corner_size"

"type": "transform.basic.move"
→ "type": "transform.basic_transforms.translate"

"type": "transform.basic.rotate"
→ "type": "transform.basic_transforms.rotate"

"type": "output.bake.geometry_to_blocks"
→ "type": "output.execute.bake_geometry_to_blocks"

"type": "output.preview.preview_blocks"
→ "type": "output.preview.block_preview"

"type": "geometry.profiles.triangle"
→ "type": "geometry.profiles.polygon_profile"

"type": "geometry.curves.divide_curve"
→ "type": "geometry.curves.divide_curve_to_points"

"type": "geometry.solids.extrude"
→ "type": "geometry.solids.extrude_profile"
```

## 执行方案

### 方案A：使用Python脚本（推荐）

在命令行运行：
```bash
cd F:\development\NC\nodecraft
python fix_all_node_ids_now.py
```

### 方案B：使用编辑器批量替换

1. 使用 VS Code 或其他编辑器打开 `F:\development\NC\nodecraft\presets` 文件夹
2. 使用 "在文件中查找和替换" 功能
3. 逐个执行上述8个替换操作
4. 确保只替换 `presets` 文件夹内的文件

### 方案C：让我逐个手动修复（耗时）

我可以逐个读取、修改、写入这20个文件，但需要大约20-30分钟。

---

## 最快的方法

如果Python脚本无法运行，请：

1. 用文本编辑器打开 `F:\development\NC\nodecraft\fix_all_node_ids_now.py`
2. 复制所有内容
3. 在命令行执行：
```bash
cd F:\development\NC\nodecraft
python
# 然后粘贴脚本内容，按回车
```

或者简化版本：
```bash
cd F:\development\NC\nodecraft
python -c "exec(open('fix_all_node_ids_now.py').read())"
```

---

**你的选择**：
- A: 运行Python脚本（最快，2秒）
- B: 使用编辑器批量替换（5分钟）
- C: 让我手动逐个修复（20分钟）

请告诉我你想使用哪个方案？
