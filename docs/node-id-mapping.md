# Node ID Mapping - Correct vs Incorrect

## 节点ID映射表

### 错误 → 正确

**Geometry Primitives:**
- ❌ `geometry.primitives.box_by_corner_and_size` → ✅ `geometry.primitives.box_from_corner_size`
- ❌ `geometry.primitives.cylinder_by_axis_and_radius` → ✅ `geometry.primitives.cylinder`

**Transform:**
- ❌ `transform.basic.move` → ✅ `transform.basic_transforms.translate`
- ❌ `transform.basic.rotate` → ✅ `transform.basic_transforms.rotate`
- ❌ `transform.basic.scale` → ✅ `transform.basic_transforms.scale`

**Material:**
- ❌ `material.gradient_mapping.height_gradient` → ✅ `material.gradient_mapping.height_gradient_map`

**Output:**
- ❌ `output.bake.geometry_to_blocks` → ✅ `output.execute.bake_geometry_to_blocks`
- ❌ `output.preview.preview_blocks` → ✅ `output.preview.block_preview`
- ❌ `output.preview.geometry_viewer` → ✅ `output.preview.geometry_preview`

**Curves:**
- ❌ `geometry.curves.divide_curve` → ✅ `geometry.curves.divide_curve_to_points`

**Profiles:**
- ❌ `geometry.profiles.triangle` → ✅ `geometry.profiles.polygon_profile` (with 3 sides)
- ❌ `geometry.profiles.rectangle` → ✅ `geometry.profiles.rectangle_profile`
- ❌ `geometry.profiles.circle` → ✅ `geometry.profiles.circle_profile`
- ❌ `geometry.profiles.arc` → ✅ `geometry.profiles.sector_profile`

**Boolean:**
- ❌ `geometry.boolean.union_multiple` → ✅ `geometry.boolean.union` (accepts multiple)

**Solids:**
- ❌ `geometry.solids.extrude` → ✅ `geometry.solids.extrude_profile`

**Patterns:**
- ❌ `patterns.array.linear` → ✅ `patterns.instances.array_linear`
- ❌ `patterns.instances.instance_on_points` → ✅ `patterns.instances.instance_geometry_to_points`

---

## 需要修正的预设文件

所有21个预设文件中使用的节点ID都需要更新为正确的ID。
