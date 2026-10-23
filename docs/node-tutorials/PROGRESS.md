# 节点教程进度追踪

> 数据来源：[`NODE_LIBRARY.zh-CN.md`](../NODE_LIBRARY.zh-CN.md)（539 节点 / 60 分类）
>
> 状态约定：`[ ]` 未写 · `[x]` 已完成 · `[-]` 暂缓
>
> 更新方式：每完成一组，把对应节点勾为 `[x]`，并在下方「批次记录」追加一行。

## 总览

| 指标 | 数值 |
|---|---:|
| 节点总数 | 539 |
| 已完成 | 539 |
| 进度 | 539 / 539（100%） |
| 当前批次 | 全部完成 |

## 批次记录

| 日期 | 分类 | 完成数 | 备注 |
|---|---|---:|---|
| 2026-10-23 | `flow.control` | 4 | 首批：流程控制 |
| 2026-10-23 | `flow.loop` | 2 | For Each / While |
| 2026-10-23 | `input.context` | 4 | 含射线示意图 |
| 2026-10-23 | `input.values` | 6 | 值输入控件 |
| 2026-10-23 | `input.type_selectors` | 4 | 方块/实体/物品/群系 |
| 2026-10-23 | `input.numeric` | 16 | 输入/滑条/角度/转换 |
| 2026-10-23 | `reference.planes` | 7 | 世界/构造/偏移/距离 |
| 2026-10-23 | `reference.frames` | 8 | 世界/构造/平面/变换/拆解 |
| 2026-10-23 | `reference.points` | 19 | 格点/连续点/盒体拓扑 |
| 2026-10-23 | `reference.vectors` | 17 | 输入/加减乘除/积与角/插值投影；`reference.*` 齐 |
| 2026-10-23 | `geometry.primitives` | 31 | 构造 17 + 拆解 14 |
| 2026-10-23 | `geometry.profiles` | 26 | 平面轮廓 / Region 布尔偏移 |
| 2026-10-23 | `geometry.sdf` | 13 | 原语/布尔/采样/To Geometry |
| 2026-10-23 | `geometry.solids` | 23 | 挤出/放样扫掠/条带/剖切 |
| 2026-10-23 | `geometry.curves` | 28 | 构造/编辑/查询/体素路径 |
| 2026-10-23 | `geometry.architectural_primitives` | 20 | 墙/洞口/柱梁/屋顶楼梯 |
| 2026-10-23 | `geometry.boolean` | 2 | Difference / Intersection |
| 2026-10-23 | `geometry.combine` | 1 | Combine Geometry |
| 2026-10-23 | `geometry.analysis` | 3 | Bounds / Convex Hull |
| 2026-10-23 | `geometry.voxel` | 2 | Voxelize / Strip→Blocks |
| 2026-10-23 | `transform.basic_transforms` | 9 | 平移旋转缩放镜像/面偏移 |
| 2026-10-23 | `transform.deformations` | 11 | 点列变形 + Twist/Bend SDF |
| 2026-10-23 | `transform.orientation` | 6 | 投影与法线对齐 |
| 2026-10-23 | `transform.placement` | 8 | Frame 放置 / 方块格变换 |
| 2026-10-23 | `pattern.grid` | 5 | 矩形/立面/交错/六角/三角 |
| 2026-10-23 | `pattern.linear` | 4 | 线性/路径阵列与方块实例 |
| 2026-10-23 | `pattern.lsystem` | 3 | 规则/展开/海龟 |
| 2026-10-23 | `pattern.radial` | 3 | 极阵/螺旋/叶序 |
| 2026-10-23 | `pattern.surface_volume_distribution` | 6 | 面体散射/泊松/图像 |
| 2026-10-23 | `pattern.voronoi_3d` | 1 | Lloyd 松弛 |
| 2026-10-23 | `material.basic_assignment` | 4 | 赋方块/调色板 |
| 2026-10-23 | `material.block_state` | 4 | 构建/朝向/楼梯 shape |
| 2026-10-23 | `material.directional_mapping` | 3 | 柱层/坡度/半砖楼梯 |
| 2026-10-23 | `material.gradient_mapping` | 5 | 高度/噪声/距离/SDF |
| 2026-10-23 | `material.pattern_mapping` | 4 | 棋盘/条纹/砖/网格 |
| 2026-10-23 | `material.surface_aging` | 3 | 风化/苔藓/裂纹 |
| 2026-10-23 | `output.debug` | 4 | 监视/聊天/计时/面板 |
| 2026-10-23 | `output.execute` | 8 | Apply/撤销/Bake/合并 |
| 2026-10-23 | `output.export` | 4 | Schematic/Litematic/WE/CSV |
| 2026-10-23 | `output.preview` | 13 | 幽灵预览全家桶 |
| 2026-10-24 | `world.query` | 11 | 格点/邻域/射线/实体 |
| 2026-10-24 | `world.read` | 11 | 读方块/高度图/NBT |
| 2026-10-24 | `world.selection` | 8 | 选区源与贴格 |
| 2026-10-24 | `world.terrain` | 19 | 高度/侵蚀/气候场 |
| 2026-10-24 | `world.write` | 18 | 放置/实体/write 撤销 |
| 2026-10-24 | `math.compare` | 6 | 比较运算 |
| 2026-10-24 | `math.data_tree` | 14 | 数据树 |
| 2026-10-24 | `math.fields` | 17 | 标量/向量场 |
| 2026-10-24 | `math.list` | 24 | 列表操作 |
| 2026-10-24 | `math.logic` | 6 | 逻辑门与分支 |
| 2026-10-24 | `math.random` | 6 | 随机与噪声 |
| 2026-10-24 | `math.scalar_math` | 23 | 标量运算 |
| 2026-10-24 | `math.sequence` | 3 | 序列 |
| 2026-10-24 | `math.trigonometry` | 10 | 三角/双曲 |
| 2026-10-24 | `math.vector` | 1 | 分量 min/max |
| 2026-10-24 | `utilities.assist` | 5 | 格式/校验/中继 |
| 2026-10-24 | `utilities.fileio` | 3 | 图像/VOX |
| 2026-10-24 | `utilities.morphology` | 1 | 方块形态学 |
| 2026-10-24 | `utilities.organization` | 3 | 图 I/O / 子图 |
| 2026-10-24 | `variable` | 6 | 变量读写 |
| 2026-10-24 | （润色） | — | 中文「作用」；补齐使用步骤/典型接法；README 分类索引 |
| 2026-10-24 | （示意图） | 15 | 墙洞/楼梯/布尔/SDF/放样扫掠/Frame/树/海龟/泛洪/地形等 |
| 2026-10-24 | （示意图·扩） | 16 | 回转/剖切/晶格/散射/加厚/双轨/射线/高度场/窗阵/展平/挤出/扭等 |
| 2026-10-24 | （示意图·扩2） | 17 | 弯折/吸引/多截面放样/图像散射/水力侵蚀/屋顶拱/极阵/分区等 |
| 2026-10-24 | （示意图·扩3） | 16 | Tween/剪切/热侵蚀/群系/Shift/倒圆偏移/2D布尔/半砖楼梯/吸引子等 |

---

## `flow.control`

> 目录：`flow/control/`

- [x] **Branch** (`flow.control.branch`) → `branch.md`
- [x] **Sequence** (`flow.control.sequence`) → `sequence.md`
- [x] **Do Once** (`flow.control.do_once`) → `do_once.md`
- [x] **Manual Trigger** (`flow.control.manual_trigger`) → `manual_trigger.md`

## `flow.loop`

> 目录：`flow/loop/`

- [x] **For Each Loop** (`flow.loop.for_each`) → `for_each.md`
- [x] **While Loop** (`flow.loop.while`) → `while.md`

## `geometry.analysis`

> 目录：`geometry/analysis/`

- [x] **Block Bounds** (`geometry.analysis.block_bounds`) → `block_bounds.md`
- [x] **Geometry Bounds** (`geometry.analysis.geometry_bounds`) → `geometry_bounds.md`
- [x] **Convex Hull 3D From Points** (`geometry.analysis.convex_hull_3d`) → `convex_hull_3d.md`

## `geometry.architectural_primitives`

> 目录：`geometry/architectural_primitives/`

- [x] **Window Array** (`geometry.architectural_primitives.window_array`) → `window_array.md`
- [x] **Door Array** (`geometry.architectural_primitives.door_array`) → `door_array.md`
- [x] **Column Grid** (`geometry.architectural_primitives.column_grid`) → `column_grid.md`
- [x] **Railing** (`geometry.architectural_primitives.railing`) → `railing.md`
- [x] **Roof Base** (`geometry.architectural_primitives.roof_base`) → `roof_base.md`
- [x] **Staircase** (`geometry.architectural_primitives.staircase`) → `staircase.md`
- [x] **Roof Generator** (`geometry.architectural_primitives.roof_generator`) → `roof_generator.md`
- [x] **Facade Panel Array** (`geometry.architectural_primitives.facade_panel_array`) → `facade_panel_array.md`
- [x] **Arch Opening** (`geometry.architectural_primitives.arch_opening`) → `arch_opening.md`
- [x] **Wall With Openings** (`geometry.architectural_primitives.wall_with_openings`) → `wall_with_openings.md`
- [x] **Pilaster / Cornice** (`geometry.architectural_primitives.pilaster_cornice`) → `pilaster_cornice.md`
- [x] **Array Along Curve** (`geometry.architectural_primitives.array_along_curve`) → `array_along_curve.md`
- [x] **Floor Slab** (`geometry.architectural_primitives.floor_slab`) → `floor_slab.md`
- [x] **Beam Grid** (`geometry.architectural_primitives.beam_grid`) → `beam_grid.md`
- [x] **Molding Profile** (`geometry.architectural_primitives.molding_profile`) → `molding_profile.md`
- [x] **Wall Along Path** (`geometry.architectural_primitives.wall_along_path`) → `wall_along_path.md`
- [x] **Beam Along Path** (`geometry.architectural_primitives.beam_along_path`) → `beam_along_path.md`
- [x] **Column** (`geometry.architectural_primitives.column`) → `column.md`
- [x] **Window Frame** (`geometry.architectural_primitives.window_frame`) → `window_frame.md`
- [x] **Wall Slab** (`geometry.architectural_primitives.wall_slab`) → `wall_slab.md`

## `geometry.boolean`

> 目录：`geometry/boolean/`

- [x] **Difference** (`geometry.boolean.difference`) → `difference.md`
- [x] **Intersection** (`geometry.boolean.intersection`) → `intersection.md`

## `geometry.combine`

> 目录：`geometry/combine/`

- [x] **Combine Geometry** (`geometry.combine.geometry`) → `geometry.md`

## `geometry.curves`

> 目录：`geometry/curves/`

- [x] **Points To Path** (`geometry.curves.points_to_path`) → `points_to_path.md`
- [x] **Extract Path Points** (`geometry.curves.path_to_points`) → `path_to_points.md`
- [x] **Face Edge To Path** (`geometry.curves.edge_to_curve`) → `edge_to_curve.md`
- [x] **Box Face Boundary Path** (`geometry.curves.face_boundary_curve`) → `face_boundary_curve.md`
- [x] **Arc** (`geometry.curves.arc`) → `arc.md`
- [x] **Bezier** (`geometry.curves.bezier`) → `bezier.md`
- [x] **Interpolate Spline** (`geometry.curves.interpolate_spline`) → `interpolate_spline.md`
- [x] **B-Spline** (`geometry.curves.b_spline`) → `b_spline.md`
- [x] **NURBS Curve** (`geometry.curves.nurbs`) → `nurbs.md`
- [x] **Parabola On Plane** (`geometry.curves.parabola_curve`) → `parabola_curve.md`
- [x] **Helix Curve** (`geometry.curves.helix`) → `helix.md`
- [x] **Infinity Curve On Plane** (`geometry.curves.infinity_curve`) → `infinity_curve.md`
- [x] **Join Paths** (`geometry.curves.join_paths`) → `join_paths.md`
- [x] **Reverse Path** (`geometry.curves.reverse_path`) → `reverse_path.md`
- [x] **Split Path** (`geometry.curves.split_path`) → `split_path.md`
- [x] **Trim Path** (`geometry.curves.trim_path`) → `trim_path.md`
- [x] **Explode Path** (`geometry.curves.explode_path`) → `explode_path.md`
- [x] **Extend Path** (`geometry.curves.extend_path`) → `extend_path.md`
- [x] **Fillet Path Corners** (`geometry.curves.fillet_polyline_corners`) → `fillet_polyline_corners.md`
- [x] **Offset Path In Plane** (`geometry.curves.offset_curve_plane`) → `offset_curve_plane.md`
- [x] **Resample Path** (`geometry.curves.resample_path`) → `resample_path.md`
- [x] **Path Length** (`geometry.curves.path_length`) → `path_length.md`
- [x] **Evaluate Path** (`geometry.curves.evaluate_curve`) → `evaluate_curve.md`
- [x] **Closest Point On Path** (`geometry.curves.closest_point_on_path`) → `closest_point_on_path.md`
- [x] **Rainbow Curve Offset** (`geometry.curves.rainbow_curve_offset`) → `rainbow_curve_offset.md`
- [x] **Blend Paths** (`geometry.curves.blend_curves`) → `blend_curves.md`
- [x] **Tween Paths** (`geometry.curves.tween_curves`) → `tween_curves.md`
- [x] **Voxelize Path** (`geometry.curves.voxelize_curve`) → `voxelize_curve.md`

## `geometry.primitives`

> 目录：`geometry/primitives/`

- [x] **Box by Center + Size** (`geometry.primitives.box`) → `box.md`
- [x] **Box by Corner + Size** (`geometry.primitives.box_from_corner_size`) → `box_from_corner_size.md`
- [x] **Box by Two Corners** (`geometry.primitives.box_from_corners`) → `box_from_corners.md`
- [x] **Sphere By Center Radius** (`geometry.primitives.sphere`) → `sphere.md`
- [x] **Sphere By Diameter** (`geometry.primitives.sphere_from_diameter`) → `sphere_from_diameter.md`
- [x] **Cylinder By Axis Radius** (`geometry.primitives.cylinder`) → `cylinder.md`
- [x] **Cone By Base Apex Radius** (`geometry.primitives.cone`) → `cone.md`
- [x] **Frustum By Two Centers Radii** (`geometry.primitives.frustum_cone`) → `frustum_cone.md`
- [x] **Capsule By Axis Radius** (`geometry.primitives.capsule`) → `capsule.md`
- [x] **Hemisphere By Center Axis Radius** (`geometry.primitives.hemisphere`) → `hemisphere.md`
- [x] **Torus By Center Axis Radii** (`geometry.primitives.torus`) → `torus.md`
- [x] **Ellipsoid By Center Radii** (`geometry.primitives.ellipsoid`) → `ellipsoid.md`
- [x] **Square Pyramid** (`geometry.primitives.square_pyramid`) → `square_pyramid.md`
- [x] **Tetrahedron By Center Edge** (`geometry.primitives.tetrahedron`) → `tetrahedron.md`
- [x] **Octahedron By Center Size** (`geometry.primitives.octahedron`) → `octahedron.md`
- [x] **Icosahedron By Center Edge** (`geometry.primitives.icosahedron`) → `icosahedron.md`
- [x] **Dodecahedron By Center Edge** (`geometry.primitives.dodecahedron`) → `dodecahedron.md`
- [x] **Deconstruct Box Geometry** (`geometry.primitives.deconstruct_box`) → `deconstruct_box.md`
- [x] **Deconstruct Sphere** (`geometry.primitives.deconstruct_sphere`) → `deconstruct_sphere.md`
- [x] **Deconstruct Cylinder** (`geometry.primitives.deconstruct_cylinder`) → `deconstruct_cylinder.md`
- [x] **Deconstruct Cone** (`geometry.primitives.deconstruct_cone`) → `deconstruct_cone.md`
- [x] **Deconstruct Frustum Cone** (`geometry.primitives.deconstruct_frustum_cone`) → `deconstruct_frustum_cone.md`
- [x] **Deconstruct Hemisphere** (`geometry.primitives.deconstruct_hemisphere`) → `deconstruct_hemisphere.md`
- [x] **Deconstruct Ellipsoid** (`geometry.primitives.deconstruct_ellipsoid`) → `deconstruct_ellipsoid.md`
- [x] **Deconstruct Prism** (`geometry.primitives.deconstruct_prism`) → `deconstruct_prism.md`
- [x] **Deconstruct Tetrahedron** (`geometry.primitives.deconstruct_tetrahedron`) → `deconstruct_tetrahedron.md`
- [x] **Deconstruct Octahedron** (`geometry.primitives.deconstruct_octahedron`) → `deconstruct_octahedron.md`
- [x] **Deconstruct Icosahedron** (`geometry.primitives.deconstruct_icosahedron`) → `deconstruct_icosahedron.md`
- [x] **Deconstruct Dodecahedron** (`geometry.primitives.deconstruct_dodecahedron`) → `deconstruct_dodecahedron.md`
- [x] **Deconstruct Torus** (`geometry.primitives.deconstruct_torus`) → `deconstruct_torus.md`
- [x] **Deconstruct Capsule** (`geometry.primitives.deconstruct_capsule`) → `deconstruct_capsule.md`

## `geometry.profiles`

> 目录：`geometry/profiles/`

- [x] **Polygon By Points** (`geometry.profiles.custom_profile`) → `custom_profile.md`
- [x] **Rectangle On Plane** (`geometry.profiles.rectangle_profile`) → `rectangle_profile.md`
- [x] **Rounded Rectangle On Plane** (`geometry.profiles.rounded_rectangle_profile`) → `rounded_rectangle_profile.md`
- [x] **Circle On Plane** (`geometry.profiles.circle_profile`) → `circle_profile.md`
- [x] **Ellipse On Plane** (`geometry.profiles.ellipse_profile`) → `ellipse_profile.md`
- [x] **Regular Polygon On Plane** (`geometry.profiles.polygon_profile`) → `polygon_profile.md`
- [x] **Star Polygon On Plane** (`geometry.profiles.star_polygon_profile`) → `star_polygon_profile.md`
- [x] **Rhombus On Plane** (`geometry.profiles.rhombus_profile`) → `rhombus_profile.md`
- [x] **Cross On Plane** (`geometry.profiles.cross_profile`) → `cross_profile.md`
- [x] **Capsule On Plane** (`geometry.profiles.capsule_profile`) → `capsule_profile.md`
- [x] **Heart On Plane** (`geometry.profiles.heart_profile`) → `heart_profile.md`
- [x] **SemiCircle On Plane** (`geometry.profiles.semicircle_profile`) → `semicircle_profile.md`
- [x] **Sector On Plane** (`geometry.profiles.sector_profile`) → `sector_profile.md`
- [x] **Annulus On Plane** (`geometry.profiles.annulus_profile`) → `annulus_profile.md`
- [x] **Annular Sector On Plane** (`geometry.profiles.annular_sector_profile`) → `annular_sector_profile.md`
- [x] **Resample Polygon Profile** (`geometry.profiles.resample_profile`) → `resample_profile.md`
- [x] **Profile Offset In Plane** (`geometry.profiles.offset_profile_plane`) → `offset_profile_plane.md`
- [x] **Profile Boolean 2D** (`geometry.profiles.boolean_2d`) → `boolean_2d.md`
- [x] **Convex Hull 2D On Plane** (`geometry.profiles.convex_hull_plane`) → `convex_hull_plane.md`
- [x] **Voronoi Cells 2D On Plane** (`geometry.profiles.voronoi_cells_plane`) → `voronoi_cells_plane.md`
- [x] **Profile Triangulate 2D** (`geometry.profiles.triangulate_2d`) → `triangulate_2d.md`
- [x] **Deconstruct Polygon Profile** (`geometry.profiles.deconstruct_profile`) → `deconstruct_profile.md`
- [x] **Gear On Plane** (`geometry.profiles.gear_profile`) → `gear_profile.md`
- [x] **Profile To Region** (`geometry.profiles.profile_to_region`) → `profile_to_region.md`
- [x] **Region Boolean 2D** (`geometry.profiles.region_boolean_2d`) → `region_boolean_2d.md`
- [x] **Region Offset In Plane** (`geometry.profiles.region_offset_plane`) → `region_offset_plane.md`

## `geometry.sdf`

> 目录：`geometry/sdf/`

- [x] **SDF Sphere** (`geometry.boolean.sdf_sphere`) → `sdf_sphere.md`
- [x] **SDF Box** (`geometry.boolean.sdf_box`) → `sdf_box.md`
- [x] **SDF Capsule** (`geometry.boolean.sdf_capsule`) → `sdf_capsule.md`
- [x] **SDF Torus** (`geometry.boolean.sdf_torus`) → `sdf_torus.md`
- [x] **SDF Boolean** (`geometry.boolean.sdf_boolean`) → `sdf_boolean.md`
- [x] **SDF To Geometry** (`geometry.boolean.sdf_to_geometry`) → `sdf_to_geometry.md`
- [x] **SDF Sample Point** (`geometry.boolean.sdf_sample_point`) → `sdf_sample_point.md`
- [x] **SDF Sample Points** (`geometry.boolean.sdf_sample_points`) → `sdf_sample_points.md`
- [x] **SDF Gradient At Point** (`geometry.boolean.sdf_gradient_point`) → `sdf_gradient_point.md`
- [x] **SDF Noise Displace** (`geometry.boolean.sdf_noise_displace`) → `sdf_noise_displace.md`
- [x] **SDF Transform** (`geometry.boolean.sdf_transform`) → `sdf_transform.md`
- [x] **SDF Blend Material Mask** (`geometry.boolean.sdf_blend_material_mask`) → `sdf_blend_material_mask.md`
- [x] **SDF Domain Warp** (`geometry.boolean.sdf_domain_warp`) → `sdf_domain_warp.md`

## `geometry.solids`

> 目录：`geometry/solids/`

- [x] **Extrude** (`geometry.solids.extrude`) → `extrude.md`
- [x] **Extrude Point List** (`geometry.solids.extrude_from_points`) → `extrude_from_points.md`
- [x] **Prism By Points** (`geometry.solids.extrude_profile_from_points`) → `extrude_profile_from_points.md`
- [x] **Extrude Box Face** (`geometry.solids.extrude_box_face`) → `extrude_box_face.md`
- [x] **Push/Pull Box Face** (`geometry.solids.push_pull_face`) → `push_pull_face.md`
- [x] **Loft Surface** (`geometry.solids.loft`) → `loft.md`
- [x] **Loft Point Lists** (`geometry.solids.loft_from_points`) → `loft_from_points.md`
- [x] **Multi-Section Loft Surface** (`geometry.solids.loft_multi_section`) → `loft_multi_section.md`
- [x] **Sweep Surface** (`geometry.solids.sweep`) → `sweep.md`
- [x] **Sweep Surface From Points** (`geometry.solids.sweep_from_points`) → `sweep_from_points.md`
- [x] **Sweep 2 Rails** (`geometry.solids.sweep_two_rails`) → `sweep_two_rails.md`
- [x] **Revolve Surface** (`geometry.solids.revolve`) → `revolve.md`
- [x] **Morph Between Profiles** (`geometry.solids.morph_profiles`) → `morph_profiles.md`
- [x] **Offset Surface Strip** (`geometry.solids.offset_surface_strip`) → `offset_surface_strip.md`
- [x] **Thicken Surface** (`geometry.solids.thicken_surface`) → `thicken_surface.md`
- [x] **Extract Surface Strip Range** (`geometry.solids.extract_surface_strip_range`) → `extract_surface_strip_range.md`
- [x] **Deconstruct Surface Strip** (`geometry.solids.deconstruct_surface_strip`) → `deconstruct_surface_strip.md`
- [x] **Surface Strip To Lattice** (`geometry.solids.surface_strip_to_lattice`) → `surface_strip_to_lattice.md`
- [x] **Voxel Section** (`geometry.solids.section_cut`) → `section_cut.md`
- [x] **Voxel Contours** (`geometry.solids.contour`) → `contour.md`
- [x] **Shrinkwrap Points On Surface Strip** (`geometry.solids.shrinkwrap_points_surface_strip`) → `shrinkwrap_points_surface_strip.md`
- [x] **Shrinkwrap Points To Voxel Geometry** (`geometry.solids.shrinkwrap_points_voxel_geometry`) → `shrinkwrap_points_voxel_geometry.md`
- [x] **Extrude Region** (`geometry.solids.extrude_region`) → `extrude_region.md`

## `geometry.voxel`

> 目录：`geometry/voxel/`

- [x] **Voxelize Geometry** (`geometry.voxel.voxelize_geometry`) → `voxelize_geometry.md`
- [x] **Surface Strip To Blocks** (`geometry.voxel.surface_strip_to_blocks`) → `surface_strip_to_blocks.md`

## `input.context`

> 目录：`input/context/`

- [x] **Player Position Snapshot** (`input.context.player_position`) → `player_position.md`
- [x] **Player View Raycast** (`input.context.player_raycast`) → `player_raycast.md`
- [x] **Dimension Info** (`input.context.dimension_info`) → `dimension_info.md`
- [x] **Current Time** (`input.context.current_time`) → `current_time.md`

## `input.numeric`

> 目录：`input/numeric/`

- [x] **Integer Input** (`input.numeric.integer`) → `integer.md`
- [x] **Number Input** (`input.numeric.float`) → `float.md`
- [x] **Integer Slider** (`input.numeric.integer_slider`) → `integer_slider.md`
- [x] **Number Slider** (`input.numeric.float_slider`) → `float_slider.md`
- [x] **Angle Slider** (`input.numeric.angle`) → `angle.md`
- [x] **Circular Angle Picker** (`input.numeric.angle_picker`) → `angle_picker.md`
- [x] **XY Slider** (`input.numeric.xy_slider`) → `xy_slider.md`
- [x] **Domain Input** (`input.numeric.range`) → `range.md`
- [x] **Integer To Double** (`input.numeric.integer_to_double`) → `integer_to_double.md`
- [x] **Pi** (`input.numeric.pi`) → `pi.md`
- [x] **Double To Integer** (`input.numeric.double_to_integer`) → `double_to_integer.md`
- [x] **E** (`input.numeric.e`) → `e.md`
- [x] **Float To Double** (`input.numeric.float_to_double`) → `float_to_double.md`
- [x] **Double To Float** (`input.numeric.double_to_float`) → `double_to_float.md`
- [x] **Integer To Float** (`input.numeric.integer_to_float`) → `integer_to_float.md`
- [x] **Float To Integer** (`input.numeric.float_to_integer`) → `float_to_integer.md`

## `input.type_selectors`

> 目录：`input/type_selectors/`

- [x] **Block Type Selector** (`input.type_selectors.block_type_selector`) → `block_type_selector.md`
- [x] **Entity Type Selector** (`input.type_selectors.entity_type_selector`) → `entity_type_selector.md`
- [x] **Item Type Selector** (`input.type_selectors.item_type_selector`) → `item_type_selector.md`
- [x] **Biome Selector** (`input.type_selectors.biome_selector`) → `biome_selector.md`

## `input.values`

> 目录：`input/values/`

- [x] **Text Input** (`input.values.text_input`) → `text_input.md`
- [x] **Color Picker** (`input.values.color_picker`) → `color_picker.md`
- [x] **Boolean Toggle** (`input.values.boolean_toggle`) → `boolean_toggle.md`
- [x] **Gradient Ramp** (`input.values.gradient_ramp`) → `gradient_ramp.md`
- [x] **Value List** (`input.values.dropdown`) → `dropdown.md`
- [x] **File Path Input** (`input.values.file_path`) → `file_path.md`

## `material.basic_assignment`

> 目录：`material/basic_assignment/`

- [x] **Assign Block Type** (`material.basic_assignment.assign_block_type`) → `assign_block_type.md`
- [x] **Create Block Palette** (`material.basic_assignment.create_block_palette`) → `create_block_palette.md`
- [x] **Block Palette** (`material.basic_assignment.block_palette`) → `block_palette.md`
- [x] **Weighted Spatial Palette** (`material.basic_assignment.weighted_palette`) → `weighted_palette.md`

## `material.block_state`

> 目录：`material/block_state/`

- [x] **Build Block State** (`material.block_state.build_block_state`) → `build_block_state.md`
- [x] **Orient Block State** (`material.block_state.orient_block_state`) → `orient_block_state.md`
- [x] **Apply Block State** (`material.block_state.apply_block_state`) → `apply_block_state.md`
- [x] **Stair Shape** (`material.block_state.stair_shape`) → `stair_shape.md`

## `material.directional_mapping`

> 目录：`material/directional_mapping/`

- [x] **Column Layer Map** (`material.directional_mapping.top_side_bottom_map`) → `top_side_bottom_map.md`
- [x] **Surface Slope Map** (`material.directional_mapping.slope_map`) → `slope_map.md`
- [x] **Slab / Stair Adapt** (`material.directional_mapping.slab_stair_autofill`) → `slab_stair_autofill.md`

## `material.gradient_mapping`

> 目录：`material/gradient_mapping/`

- [x] **Height Gradient Map** (`material.gradient_mapping.height_gradient_map`) → `height_gradient_map.md`
- [x] **Noise Material** (`material.gradient_mapping.noise_material`) → `noise_material.md`
- [x] **Height Palette Map** (`material.gradient_mapping.gradient_ramp_map`) → `gradient_ramp_map.md`
- [x] **Distance-Based Material** (`material.gradient_mapping.distance_material`) → `distance_material.md`
- [x] **SDF-Driven Material** (`material.gradient_mapping.sdf_material`) → `sdf_material.md`

## `material.pattern_mapping`

> 目录：`material/pattern_mapping/`

- [x] **Checker Pattern Map** (`material.pattern_mapping.checker_pattern_map`) → `checker_pattern_map.md`
- [x] **Stripe Pattern Map** (`material.pattern_mapping.stripe_pattern_map`) → `stripe_pattern_map.md`
- [x] **Brick Pattern Map** (`material.pattern_mapping.brick_pattern_map`) → `brick_pattern_map.md`
- [x] **Grid Pattern Map** (`material.pattern_mapping.grid_pattern_map`) → `grid_pattern_map.md`

## `material.surface_aging`

> 目录：`material/surface_aging/`

- [x] **Weathering** (`material.surface_aging.weathering`) → `weathering.md`
- [x] **Moss Growth** (`material.surface_aging.moss_growth`) → `moss_growth.md`
- [x] **Surface Cracks** (`material.surface_aging.crack_pattern`) → `crack_pattern.md`

## `math.compare`

> 目录：`math/compare/`

- [x] **Equals (==)** (`math.compare.equals`) → `equals.md`
- [x] **Not Equals (!=)** (`math.compare.not_equals`) → `not_equals.md`
- [x] **Less Than (<)** (`math.compare.less_than`) → `less_than.md`
- [x] **Less Than or Equal (<=)** (`math.compare.less_than_or_equal`) → `less_than_or_equal.md`
- [x] **Greater Than (>)** (`math.compare.greater_than`) → `greater_than.md`
- [x] **Greater Than or Equal (>=)** (`math.compare.greater_than_or_equal`) → `greater_than_or_equal.md`

## `math.data_tree`

> 目录：`math/data_tree/`

- [x] **Construct Tree Path** (`math.data_tree.tree_path`) → `tree_path.md`
- [x] **Graft List** (`math.data_tree.graft_list`) → `graft_list.md`
- [x] **Flatten Tree** (`math.data_tree.flatten`) → `flatten.md`
- [x] **Partition List To Tree** (`math.data_tree.partition_list`) → `partition_list.md`
- [x] **Tree Branch** (`math.data_tree.branch`) → `branch.md`
- [x] **Tree Item** (`math.data_tree.item`) → `item.md`
- [x] **Tree Statistics** (`math.data_tree.statistics`) → `statistics.md`
- [x] **Tree Viewer** (`math.data_tree.viewer`) → `viewer.md`
- [x] **Merge Trees** (`math.data_tree.merge`) → `merge.md`
- [x] **Simplify Tree** (`math.data_tree.simplify`) → `simplify.md`
- [x] **Shift Path** (`math.data_tree.shift_path`) → `shift_path.md`
- [x] **Tree Paths** (`math.data_tree.paths`) → `paths.md`
- [x] **Cull Empty Branches** (`math.data_tree.cull_empty`) → `cull_empty.md`
- [x] **Entwine** (`math.data_tree.entwine`) → `entwine.md`

## `math.fields`

> 目录：`math/fields/`

- [x] **Scalar Field Constant** (`math.fields.scalar_constant`) → `scalar_constant.md`
- [x] **Scalar Field From SDF** (`math.fields.scalar_from_sdf`) → `scalar_from_sdf.md`
- [x] **Scalar Field Noise** (`math.fields.scalar_noise`) → `scalar_noise.md`
- [x] **Combine Scalar Fields** (`math.fields.scalar_binary_op`) → `scalar_binary_op.md`
- [x] **Vector Field Constant** (`math.fields.vector_constant`) → `vector_constant.md`
- [x] **Vector Field From SDF Gradient** (`math.fields.vector_from_sdf_gradient`) → `vector_from_sdf_gradient.md`
- [x] **Combine Vector Fields** (`math.fields.vector_binary_op`) → `vector_binary_op.md`
- [x] **Point Attractor Field** (`math.fields.point_attractor_field`) → `point_attractor_field.md`
- [x] **Scalar Field Sample Point** (`math.fields.scalar_sample_point`) → `scalar_sample_point.md`
- [x] **Path Attractor Field** (`math.fields.curve_attractor_field`) → `curve_attractor_field.md`
- [x] **Scalar Field Sample Points** (`math.fields.scalar_sample_points`) → `scalar_sample_points.md`
- [x] **Vector Field Sample Point** (`math.fields.vector_sample_point`) → `vector_sample_point.md`
- [x] **Volume Attractor Field** (`math.fields.volume_attractor_field`) → `volume_attractor_field.md`
- [x] **Vector Field Sample Points** (`math.fields.vector_sample_points`) → `vector_sample_points.md`
- [x] **Vortex Field** (`math.fields.vortex_field`) → `vortex_field.md`
- [x] **Repulsor Field** (`math.fields.repulsor_field`) → `repulsor_field.md`
- [x] **Blend Vector Fields** (`math.fields.attractor_blend`) → `attractor_blend.md`

## `math.list`

> 目录：`math/list/`

- [x] **Deduplicate List** (`math.list.deduplicate`) → `deduplicate.md`
- [x] **List Statistics** (`math.list.statistics`) → `statistics.md`
- [x] **Map Numbers** (`math.list.map_numbers`) → `map_numbers.md`
- [x] **Sort Numbers** (`math.list.sort_numbers`) → `sort_numbers.md`
- [x] **Sort Text** (`math.list.sort_text`) → `sort_text.md`
- [x] **Sum Numbers** (`math.list.sum_numbers`) → `sum_numbers.md`
- [x] **Product Numbers** (`math.list.product_numbers`) → `product_numbers.md`
- [x] **Min Number** (`math.list.min_number`) → `min_number.md`
- [x] **Max Number** (`math.list.max_number`) → `max_number.md`
- [x] **Average** (`math.list.average`) → `average.md`
- [x] **Join Strings** (`math.list.join_strings`) → `join_strings.md`
- [x] **Create List** (`math.list.create_list`) → `create_list.md`
- [x] **Dispatch List** (`math.list.dispatch_list`) → `dispatch_list.md`
- [x] **Filter List** (`math.list.filter_list`) → `filter_list.md`
- [x] **Flatten List** (`math.list.flatten_list`) → `flatten_list.md`
- [x] **Get Item** (`math.list.get_item`) → `get_item.md`
- [x] **Group List** (`math.list.group_list`) → `group_list.md`
- [x] **Insert Item** (`math.list.insert_item`) → `insert_item.md`
- [x] **List Length** (`math.list.list_length`) → `list_length.md`
- [x] **Remove Item** (`math.list.remove_item`) → `remove_item.md`
- [x] **Reverse List** (`math.list.reverse_list`) → `reverse_list.md`
- [x] **Set Item** (`math.list.set_item`) → `set_item.md`
- [x] **Shuffle List** (`math.list.shuffle_list`) → `shuffle_list.md`
- [x] **Sub List** (`math.list.sub_list`) → `sub_list.md`

## `math.logic`

> 目录：`math/logic/`

- [x] **If** (`math.logic.if`) → `if.md`
- [x] **Switch** (`math.logic.switch`) → `switch.md`
- [x] **AND** (`math.logic.and`) → `and.md`
- [x] **OR** (`math.logic.or`) → `or.md`
- [x] **NOT** (`math.logic.not`) → `not.md`
- [x] **XOR** (`math.logic.xor`) → `xor.md`

## `math.random`

> 目录：`math/random/`

- [x] **Random Number** (`math.random.random_number`) → `random_number.md`
- [x] **Random Numbers** (`math.random.random_numbers`) → `random_numbers.md`
- [x] **Random List Item** (`math.random.random_list_item`) → `random_list_item.md`
- [x] **Random Vector** (`math.random.random_vector`) → `random_vector.md`
- [x] **Random Vectors** (`math.random.random_vectors`) → `random_vectors.md`
- [x] **Noise** (`math.random.noise`) → `noise.md`

## `math.scalar_math`

> 目录：`math/scalar_math/`

- [x] **Addition (+)** (`math.scalar_math.addition`) → `addition.md`
- [x] **Subtraction (-)** (`math.scalar_math.subtraction`) → `subtraction.md`
- [x] **Multiplication (*)** (`math.scalar_math.multiplication`) → `multiplication.md`
- [x] **Division (/)** (`math.scalar_math.division`) → `division.md`
- [x] **Modulus (%)** (`math.scalar_math.modulus`) → `modulus.md`
- [x] **Power (^)** (`math.scalar_math.power`) → `power.md`
- [x] **Logarithm (log)** (`math.scalar_math.logarithm`) → `logarithm.md`
- [x] **Absolute (Abs)** (`math.scalar_math.absolute`) → `absolute.md`
- [x] **Min** (`math.scalar_math.min`) → `min.md`
- [x] **Max** (`math.scalar_math.max`) → `max.md`
- [x] **Clamp** (`math.scalar_math.clamp`) → `clamp.md`
- [x] **Remap** (`math.scalar_math.remap`) → `remap.md`
- [x] **Floor** (`math.scalar_math.floor`) → `floor.md`
- [x] **Ceiling** (`math.scalar_math.ceiling`) → `ceiling.md`
- [x] **Round** (`math.scalar_math.round`) → `round.md`
- [x] **Square Root** (`math.scalar_math.sqrt`) → `sqrt.md`
- [x] **Lerp** (`math.scalar_math.lerp`) → `lerp.md`
- [x] **Integer Divide** (`math.scalar_math.int_divide`) → `int_divide.md`
- [x] **Smoothstep** (`math.scalar_math.smoothstep`) → `smoothstep.md`
- [x] **Sign** (`math.scalar_math.sign`) → `sign.md`
- [x] **Fraction (Frac)** (`math.scalar_math.frac`) → `frac.md`
- [x] **Graph Mapper** (`math.scalar_math.graph_mapper`) → `graph_mapper.md`
- [x] **Expression** (`math.scalar_math.expression`) → `expression.md`

## `math.sequence`

> 目录：`math/sequence/`

- [x] **Number Sequence** (`math.sequence.range`) → `range.md`
- [x] **Repeat Item** (`math.sequence.repeat`) → `repeat.md`
- [x] **Number Series** (`math.sequence.series`) → `series.md`

## `math.trigonometry`

> 目录：`math/trigonometry/`

- [x] **Sine (Sin)** (`math.trigonometry.sin`) → `sin.md`
- [x] **Cosine (Cos)** (`math.trigonometry.cos`) → `cos.md`
- [x] **Tangent (Tan)** (`math.trigonometry.tan`) → `tan.md`
- [x] **Arcsine (ArcSin)** (`math.trigonometry.asin`) → `asin.md`
- [x] **Arccosine (ArcCos)** (`math.trigonometry.acos`) → `acos.md`
- [x] **Arctangent (ArcTan)** (`math.trigonometry.atan`) → `atan.md`
- [x] **Atan2** (`math.trigonometry.atan2`) → `atan2.md`
- [x] **Sinh** (`math.trigonometry.sinh`) → `sinh.md`
- [x] **Cosh** (`math.trigonometry.cosh`) → `cosh.md`
- [x] **Tanh** (`math.trigonometry.tanh`) → `tanh.md`

## `math.vector`

> 目录：`math/vector/`

- [x] **Vector Component Min/Max** (`math.vector.component_minmax`) → `component_minmax.md`

## `output.debug`

> 目录：`output/debug/`

- [x] **Value Monitor** (`output.debug.value_monitor`) → `value_monitor.md`
- [x] **Print To Chat** (`output.debug.print_to_chat`) → `print_to_chat.md`
- [x] **Stopwatch** (`output.debug.execution_timer`) → `execution_timer.md`
- [x] **Panel** (`output.debug.data_inspector`) → `data_inspector.md`

## `output.execute`

> 目录：`output/execute/`

- [x] **Apply Changes** (`output.execute.apply_changes`) → `apply_changes.md`
- [x] **Bake Status** (`output.execute.bake_status`) → `bake_status.md`
- [x] **Clear Preview** (`output.execute.clear_preview`) → `clear_preview.md`
- [x] **Undo Last Change** (`output.execute.undo_last_bake`) → `undo_last_bake.md`
- [x] **Redo Last Change** (`output.execute.redo_last_bake`) → `redo_last_bake.md`
- [x] **SDF To Blocks** (`output.execute.sdf_to_blocks`) → `sdf_to_blocks.md`
- [x] **Cancel Bake** (`output.execute.cancel_bake`) → `cancel_bake.md`
- [x] **Merge Block Placements** (`output.execute.merge_block_placements`) → `merge_block_placements.md`

## `output.export`

> 目录：`output/export/`

- [x] **Export Schematic** (`output.export.export_schematic`) → `export_schematic.md`
- [x] **Export Litematic** (`output.export.export_litematic`) → `export_litematic.md`
- [x] **Export WorldEdit** (`output.export.export_worldedit`) → `export_worldedit.md`
- [x] **Export CSV / JSON** (`output.export.export_data`) → `export_data.md`

## `output.preview`

> 目录：`output/preview/`

- [x] **Geometry Viewer** (`output.preview.geometry_viewer`) → `geometry_viewer.md`
- [x] **Preview Blocks** (`output.preview.preview_blocks`) → `preview_blocks.md`
- [x] **Preview Placements** (`output.preview.preview_placements`) → `preview_placements.md`
- [x] **Preview Points** (`output.preview.preview_points`) → `preview_points.md`
- [x] **Preview Vectors** (`output.preview.preview_vectors`) → `preview_vectors.md`
- [x] **Preview Plane** (`output.preview.preview_plane`) → `preview_plane.md`
- [x] **Preview Frame** (`output.preview.preview_frame`) → `preview_frame.md`
- [x] **Preview Curves** (`output.preview.preview_curves`) → `preview_curves.md`
- [x] **Preview Region** (`output.preview.preview_regions`) → `preview_regions.md`
- [x] **Preview Labels** (`output.preview.preview_labels`) → `preview_labels.md`
- [x] **Preview Surface Strip** (`output.preview.preview_surface_strip`) → `preview_surface_strip.md`
- [x] **Preview Profiles** (`output.preview.preview_profiles`) → `preview_profiles.md`
- [x] **Preview Geometry** (`output.preview.preview_geometry`) → `preview_geometry.md`

## `pattern.grid`

> 目录：`pattern/grid/`

- [x] **Grid Array** (`pattern.grid.grid_array`) → `grid_array.md`
- [x] **Facade Grid** (`pattern.grid.facade_grid`) → `facade_grid.md`
- [x] **Staggered Grid** (`pattern.grid.staggered_grid`) → `staggered_grid.md`
- [x] **Hex Grid** (`pattern.grid.hex_grid`) → `hex_grid.md`
- [x] **Triangular Grid** (`pattern.grid.triangular_grid`) → `triangular_grid.md`

## `pattern.linear`

> 目录：`pattern/linear/`

- [x] **Linear Array** (`pattern.linear.linear_array`) → `linear_array.md`
- [x] **Path Frames** (`pattern.linear.path_frames`) → `path_frames.md`
- [x] **Instance Block Placements** (`pattern.linear.instance_block_placements`) → `instance_block_placements.md`
- [x] **Curve Array** (`pattern.linear.curve_array`) → `curve_array.md`

## `pattern.lsystem`

> 目录：`pattern/lsystem/`

- [x] **L-System Rule** (`pattern.lsystem.rule`) → `rule.md`
- [x] **L-System Expand** (`pattern.lsystem.expand`) → `expand.md`
- [x] **L-System Turtle 3D** (`pattern.lsystem.turtle_3d`) → `turtle_3d.md`

## `pattern.radial`

> 目录：`pattern/radial/`

- [x] **Polar Array** (`pattern.radial.polar_array`) → `polar_array.md`
- [x] **Spiral** (`pattern.radial.spiral`) → `spiral.md`
- [x] **Phyllotaxis** (`pattern.radial.phyllotaxis`) → `phyllotaxis.md`

## `pattern.surface_volume_distribution`

> 目录：`pattern/surface_volume_distribution/`

- [x] **Sample Sphere Surface** (`pattern.surface_volume_distribution.sample_sphere_surface`) → `sample_sphere_surface.md`
- [x] **Scatter On Surface** (`pattern.surface_volume_distribution.scatter_surface`) → `scatter_surface.md`
- [x] **Poisson Disk On Plane** (`pattern.surface_volume_distribution.poisson_disk_plane`) → `poisson_disk_plane.md`
- [x] **Scatter On Surface Strip** (`pattern.surface_volume_distribution.scatter_surface_strip`) → `scatter_surface_strip.md`
- [x] **Scatter In Volume** (`pattern.surface_volume_distribution.scatter_volume`) → `scatter_volume.md`
- [x] **Image Scatter** (`pattern.surface_volume_distribution.image_scatter`) → `image_scatter.md`

## `pattern.voronoi_3d`

> 目录：`pattern/voronoi_3d/`

- [x] **Lloyd Relax 3D** (`pattern.voronoi_3d.lloyd_relax`) → `lloyd_relax.md`

## `reference.frames`

> 目录：`reference/frames/`

- [x] **Face Center Frame** (`reference.frames.frame_from_face`) → `frame_from_face.md`
- [x] **Sphere Surface Frame** (`reference.frames.sphere_surface_frame`) → `sphere_surface_frame.md`
- [x] **World Frame** (`reference.frames.world_frame`) → `world_frame.md`
- [x] **Construct Frame** (`reference.frames.construct_frame`) → `construct_frame.md`
- [x] **Frame From Plane** (`reference.frames.frame_from_plane`) → `frame_from_plane.md`
- [x] **Transform Frame** (`reference.frames.transform_frame`) → `transform_frame.md`
- [x] **Deconstruct Frame** (`reference.frames.deconstruct_frame`) → `deconstruct_frame.md`
- [x] **Deconstruct Frames** (`reference.frames.deconstruct_frames`) → `deconstruct_frames.md`

## `reference.planes`

> 目录：`reference/planes/`

- [x] **World Plane** (`reference.planes.world_plane`) → `world_plane.md`
- [x] **Construct Plane** (`reference.planes.construct_plane`) → `construct_plane.md`
- [x] **Construct Plane From Points** (`reference.planes.plane_from_points`) → `plane_from_points.md`
- [x] **Box Face To Plane** (`reference.planes.box_face_plane`) → `box_face_plane.md`
- [x] **Offset Plane** (`reference.planes.offset_plane`) → `offset_plane.md`
- [x] **Distance Point To Plane** (`reference.planes.distance_point_to_plane`) → `distance_point_to_plane.md`
- [x] **Deconstruct Plane** (`reference.planes.deconstruct_plane`) → `deconstruct_plane.md`

## `reference.points`

> 目录：`reference/points/`

- [x] **Block Position Input** (`reference.points.block_position`) → `block_position.md`
- [x] **Construct Block Position** (`reference.points.construct_coordinate`) → `construct_coordinate.md`
- [x] **Deconstruct Block Position** (`reference.points.deconstruct_block_position`) → `deconstruct_block_position.md`
- [x] **Block To Point** (`reference.points.point_from_block`) → `point_from_block.md`
- [x] **Construct Point** (`reference.points.construct_point`) → `construct_point.md`
- [x] **Deconstruct Point** (`reference.points.deconstruct_point`) → `deconstruct_point.md`
- [x] **Translate Point** (`reference.points.translate_point`) → `translate_point.md`
- [x] **Move Point Along Direction** (`reference.points.point_along_vector`) → `point_along_vector.md`
- [x] **Mid Point** (`reference.points.mid_point`) → `mid_point.md`
- [x] **Distance Between Points** (`reference.points.distance_between_points`) → `distance_between_points.md`
- [x] **Vector Between Points** (`reference.points.vector_between_points`) → `vector_between_points.md`
- [x] **Closest Point** (`reference.points.closest_point`) → `closest_point.md`
- [x] **Point List Center** (`reference.points.point_list_center`) → `point_list_center.md`
- [x] **Point List Bounds** (`reference.points.point_list_bounds`) → `point_list_bounds.md`
- [x] **Get Box Corner** (`reference.points.get_box_corner`) → `get_box_corner.md`
- [x] **Get Box Face** (`reference.points.get_box_face`) → `get_box_face.md`
- [x] **Get Face Edge** (`reference.points.get_face_edge`) → `get_face_edge.md`
- [x] **Deconstruct Box Face** (`reference.points.deconstruct_face`) → `deconstruct_face.md`
- [x] **Deconstruct Face Edge** (`reference.points.deconstruct_edge`) → `deconstruct_edge.md`

## `reference.vectors`

> 目录：`reference/vectors/`

- [x] **Vector Input** (`reference.vectors.vector`) → `vector.md`
- [x] **2D Vector Input** (`reference.vectors.vector2_input`) → `vector2_input.md`
- [x] **Construct Vector** (`reference.vectors.construct_vector`) → `construct_vector.md`
- [x] **Deconstruct Vector** (`reference.vectors.deconstruct_vector`) → `deconstruct_vector.md`
- [x] **Vector Length** (`reference.vectors.vector_length`) → `vector_length.md`
- [x] **Normalize Vector** (`reference.vectors.normalize_vector`) → `normalize_vector.md`
- [x] **Vector Addition (+)** (`reference.vectors.vector_addition`) → `vector_addition.md`
- [x] **Vector Subtraction (-)** (`reference.vectors.vector_subtraction`) → `vector_subtraction.md`
- [x] **Vector Scalar Multiply** (`reference.vectors.vector_scalar_multiply`) → `vector_scalar_multiply.md`
- [x] **Vector Scalar Divide** (`reference.vectors.vector_scalar_divide`) → `vector_scalar_divide.md`
- [x] **Dot Product** (`reference.vectors.dot_product`) → `dot_product.md`
- [x] **Cross Product** (`reference.vectors.cross_product`) → `cross_product.md`
- [x] **Angle Between Vectors** (`reference.vectors.angle_between`) → `angle_between.md`
- [x] **Lerp Vectors** (`reference.vectors.lerp_vectors`) → `lerp_vectors.md`
- [x] **Slerp Vectors** (`reference.vectors.slerp`) → `slerp.md`
- [x] **Reflect Vector** (`reference.vectors.reflect`) → `reflect.md`
- [x] **Project Vector onto Vector** (`reference.vectors.project`) → `project.md`

## `transform.basic_transforms`

> 目录：`transform/basic_transforms/`

- [x] **Move Geometry** (`transform.basic_transforms.move_geometry`) → `move_geometry.md`
- [x] **Rotate Geometry Around Axis** (`transform.basic_transforms.rotate_geometry_axis`) → `rotate_geometry_axis.md`
- [x] **Scale Geometry Around Point** (`transform.basic_transforms.scale_geometry_point`) → `scale_geometry_point.md`
- [x] **Transform Geometry** (`transform.basic_transforms.transform_geometry`) → `transform_geometry.md`
- [x] **Mirror Geometry About Plane** (`transform.basic_transforms.mirror_geometry_plane`) → `mirror_geometry_plane.md`
- [x] **Mirror Point List About Plane** (`transform.basic_transforms.mirror_point_list_plane`) → `mirror_point_list_plane.md`
- [x] **Transform Points by Frames** (`transform.basic_transforms.transform_by_frames`) → `transform_by_frames.md`
- [x] **Offset Box Face** (`transform.basic_transforms.offset_face`) → `offset_face.md`
- [x] **Inset Box Face** (`transform.basic_transforms.inset_face`) → `inset_face.md`

## `transform.deformations`

> 目录：`transform/deformations/`

- [x] **Twist Point List** (`transform.deformations.twist`) → `twist.md`
- [x] **Bend Point List** (`transform.deformations.bend`) → `bend.md`
- [x] **Taper Point List** (`transform.deformations.taper`) → `taper.md`
- [x] **Shear Point List** (`transform.deformations.shear_point_list`) → `shear_point_list.md`
- [x] **Noise Displace Point List** (`transform.deformations.noise_displace`) → `noise_displace.md`
- [x] **Spherical Displace** (`transform.deformations.spherical_displace`) → `spherical_displace.md`
- [x] **Path Attract Point List** (`transform.deformations.curve_attract`) → `curve_attract.md`
- [x] **Relax Point List** (`transform.deformations.relax_points`) → `relax_points.md`
- [x] **Lattice Deform Point List** (`transform.deformations.lattice_deform`) → `lattice_deform.md`
- [x] **Twist SDF** (`transform.deformations.twist_sdf`) → `twist_sdf.md`
- [x] **Bend SDF** (`transform.deformations.bend_sdf`) → `bend_sdf.md`

## `transform.orientation`

> 目录：`transform/orientation/`

- [x] **Project Point To Plane** (`transform.orientation.project_to_plane`) → `project_to_plane.md`
- [x] **Rotate Vector** (`transform.orientation.rotate_vector`) → `rotate_vector.md`
- [x] **Align Points To Surface Normals** (`transform.orientation.align_to_surface`) → `align_to_surface.md`
- [x] **Project Points To Plane** (`transform.orientation.project_points_to_plane`) → `project_points_to_plane.md`
- [x] **Project Path To Plane** (`transform.orientation.project_path_to_plane`) → `project_path_to_plane.md`
- [x] **Project Profile To Plane** (`transform.orientation.project_profile_to_plane`) → `project_profile_to_plane.md`

## `transform.placement`

> 目录：`transform/placement/`

- [x] **Place Geometry On Frames** (`transform.placement.place_geometry_on_frames`) → `place_geometry_on_frames.md`
- [x] **Place Geometry On Plane** (`transform.placement.place_geometry_on_plane`) → `place_geometry_on_plane.md`
- [x] **Apply Frame Orientation** (`transform.placement.orient_geometry_to_frame`) → `orient_geometry_to_frame.md`
- [x] **Offset Block Position** (`transform.placement.offset_block_position`) → `offset_block_position.md`
- [x] **Offset Block Positions** (`transform.placement.offset_block_positions`) → `offset_block_positions.md`
- [x] **Rotate Block Positions** (`transform.placement.rotate_block_positions`) → `rotate_block_positions.md`
- [x] **Scale Block Positions** (`transform.placement.scale_block_positions`) → `scale_block_positions.md`
- [x] **Mirror Block Positions** (`transform.placement.mirror_block_positions`) → `mirror_block_positions.md`

## `utilities.assist`

> 目录：`utilities/assist/`

- [x] **String Format** (`utilities.assist.string_format`) → `string_format.md`
- [x] **Validate** (`utilities.assist.validate`) → `validate.md`
- [x] **Coalesce** (`utilities.assist.coalesce`) → `coalesce.md`
- [x] **Relay** (`utilities.assist.relay`) → `relay.md`
- [x] **Signal Fork** (`utilities.assist.signal_fork`) → `signal_fork.md`

## `utilities.fileio`

> 目录：`utilities/fileio/`

- [x] **Read Image** (`utilities.fileio.read_image`) → `read_image.md`
- [x] **Image Sampler** (`utilities.fileio.image_sampler`) → `image_sampler.md`
- [x] **Import VOX** (`utilities.fileio.import_vox`) → `import_vox.md`

## `utilities.morphology`

> 目录：`utilities/morphology/`

- [x] **Block Morphology** (`utilities.morphology.block_list_morphology`) → `block_list_morphology.md`

## `utilities.organization`

> 目录：`utilities/organization/`

- [x] **Graph Input** (`utilities.organization.graph_input`) → `graph_input.md`
- [x] **Graph Output** (`utilities.organization.graph_output`) → `graph_output.md`
- [x] **Subgraph** (`utilities.organization.subgraph`) → `subgraph.md`

## `variable`

> 目录：`variable/`

- [x] **Set Variable** (`variable.set`) → `set.md`
- [x] **Get Variable** (`variable.get`) → `get.md`
- [x] **Variable List** (`variable.list`) → `list.md`
- [x] **Frame Local Variable** (`variable.frame_local`) → `frame_local.md`
- [x] **Remove Variable** (`variable.remove`) → `remove.md`
- [x] **Clear Variables** (`variable.clear`) → `clear.md`

## `world.query`

> 目录：`world/query/`

- [x] **Is Grid Point** (`world.query.is_grid_point`) → `is_grid_point.md`
- [x] **Filter Grid Points** (`world.query.filter_grid_points`) → `filter_grid_points.md`
- [x] **Point In Region** (`world.query.is_point_in_region`) → `is_point_in_region.md`
- [x] **Get Neighbor Blocks** (`world.query.get_neighbors`) → `get_neighbors.md`
- [x] **Flood Fill** (`world.query.flood_fill`) → `flood_fill.md`
- [x] **Raycast** (`world.query.raycast`) → `raycast.md`
- [x] **Get Light Level** (`world.query.get_light_level`) → `get_light_level.md`
- [x] **Get Fluid Level** (`world.query.get_fluid_level`) → `get_fluid_level.md`
- [x] **Filter Points By Rule** (`world.query.filter_points_by_rule`) → `filter_points_by_rule.md`
- [x] **Get Entities In Region** (`world.query.get_entities_in_region`) → `get_entities_in_region.md`
- [x] **Get Entity** (`world.query.get_entity`) → `get_entity.md`

## `world.read`

> 目录：`world/read/`

- [x] **Get Block** (`world.read.get_block`) → `get_block.md`
- [x] **Get Blocks In Region** (`world.read.get_blocks_in_region`) → `get_blocks_in_region.md`
- [x] **Find Blocks** (`world.read.find_blocks`) → `find_blocks.md`
- [x] **Get Biome** (`world.read.get_biome`) → `get_biome.md`
- [x] **Get Block Positions In Region** (`world.read.get_block_positions_in_region`) → `get_block_positions_in_region.md`
- [x] **Get Heightmap** (`world.read.get_heightmap`) → `get_heightmap.md`
- [x] **Get Surface Blocks** (`world.read.get_surface_blocks`) → `get_surface_blocks.md`
- [x] **Scan Region By Type** (`world.read.scan_region_by_type`) → `scan_region_by_type.md`
- [x] **Get Block NBT** (`world.read.get_block_nbt`) → `get_block_nbt.md`
- [x] **Get Entity NBT** (`world.read.get_entity_nbt`) → `get_entity_nbt.md`
- [x] **Read Sign Text** (`world.read.read_sign_text`) → `read_sign_text.md`

## `world.selection`

> 目录：`world/selection/`

- [x] **Selected Block** (`world.selection.selected_block`) → `selected_block.md`
- [x] **Selected Region** (`world.selection.selected_region`) → `selected_region.md`
- [x] **Snap Point To Block** (`world.selection.snap_point_to_block`) → `snap_point_to_block.md`
- [x] **Snap Point List To Blocks** (`world.selection.snap_points_to_blocks`) → `snap_points_to_blocks.md`
- [x] **Point To Block If Grid** (`world.selection.point_to_block_if_grid`) → `point_to_block_if_grid.md`
- [x] **Selected Block Sequence** (`world.selection.selected_block_sequence`) → `selected_block_sequence.md`
- [x] **Multi-Region Selection** (`world.selection.multi_region`) → `multi_region.md`
- [x] **Selected Entity** (`world.selection.selected_entity`) → `selected_entity.md`

## `world.terrain`

> 目录：`world/terrain/`

- [x] **Height Seed Field** (`world.terrain.height_seed_field`) → `height_seed_field.md`
- [x] **Plate Partition Field** (`world.terrain.plate_partition_field`) → `plate_partition_field.md`
- [x] **Orogenic Uplift Field** (`world.terrain.orogenic_uplift_field`) → `orogenic_uplift_field.md`
- [x] **Rift Subsidence Field** (`world.terrain.rift_subsidence_field`) → `rift_subsidence_field.md`
- [x] **Combine Height Fields** (`world.terrain.combine_height_fields`) → `combine_height_fields.md`
- [x] **Flow Direction Field** (`world.terrain.flow_direction_field`) → `flow_direction_field.md`
- [x] **Flow Accumulation Field** (`world.terrain.flow_accumulation_field`) → `flow_accumulation_field.md`
- [x] **River Mask Field** (`world.terrain.river_mask_field`) → `river_mask_field.md`
- [x] **Precipitation Field** (`world.terrain.precipitation_field`) → `precipitation_field.md`
- [x] **Thermal Erosion Step** (`world.terrain.thermal_erosion_step`) → `thermal_erosion_step.md`
- [x] **Hydraulic Erosion Step** (`world.terrain.hydraulic_erosion_step`) → `hydraulic_erosion_step.md`
- [x] **Deposition Step** (`world.terrain.deposition_step`) → `deposition_step.md`
- [x] **Delta Accumulate Field** (`world.terrain.delta_accumulate_field`) → `delta_accumulate_field.md`
- [x] **Temperature Field** (`world.terrain.temperature_field`) → `temperature_field.md`
- [x] **Biome Classify** (`world.terrain.biome_classify`) → `biome_classify.md`
- [x] **Heightfield To Blocks** (`world.terrain.heightfield_to_blocks`) → `heightfield_to_blocks.md`
- [x] **Biome Field To Blocks** (`world.terrain.biome_field_to_blocks`) → `biome_field_to_blocks.md`
- [x] **Scalar Field Slice To Blocks** (`world.terrain.scalar_field_slice_to_blocks`) → `scalar_field_slice_to_blocks.md`
- [x] **Sample Field On Region** (`world.terrain.sample_field_on_region`) → `sample_field_on_region.md`

## `world.write`

> 目录：`world/write/`

- [x] **Set Block** (`world.write.set_block`) → `set_block.md`
- [x] **Set Blocks** (`world.write.set_blocks`) → `set_blocks.md`
- [x] **Fill Region** (`world.write.fill_region`) → `fill_region.md`
- [x] **Replace Blocks** (`world.write.replace_blocks`) → `replace_blocks.md`
- [x] **Clone Region** (`world.write.clone_region`) → `clone_region.md`
- [x] **Clear Blocks** (`world.write.remove_blocks`) → `remove_blocks.md`
- [x] **Set Block NBT** (`world.write.set_block_nbt`) → `set_block_nbt.md`
- [x] **Spawn Entity** (`world.write.spawn_entity`) → `spawn_entity.md`
- [x] **Teleport Entity** (`world.write.entity_teleport`) → `entity_teleport.md`
- [x] **Remove Entities** (`world.write.remove_entities`) → `remove_entities.md`
- [x] **Write Sign Text** (`world.write.write_sign_text`) → `write_sign_text.md`
- [x] **Apply Redstone Power** (`world.write.apply_redstone_power`) → `apply_redstone_power.md`
- [x] **Simulate Right Click** (`world.write.simulate_right_click`) → `simulate_right_click.md`
- [x] **Execute Command** (`world.write.execute_command`) → `execute_command.md`
- [x] **Undo Last World Write** (`world.write.undo_last_write`) → `undo_last_write.md`
- [x] **Redo Last World Write** (`world.write.redo_last_write`) → `redo_last_write.md`
- [x] **Peek Last World Write Undo** (`world.write.peek_last_undo`) → `peek_last_undo.md`
- [x] **Clear World Write Undo History** (`world.write.clear_undo_history`) → `clear_undo_history.md`
