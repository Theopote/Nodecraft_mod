# NodeCraft v1.0 Node Migration Ledger

This document is the node-level canonical migration ledger for the NodeCraft v1.0 refactor.

It translates the category-level migration rules into concrete node migration decisions.

This ledger is intended to be updated during the migration itself.

## 1. How to Read This Ledger

Columns:

- `old_node_id`
  - current canonical or legacy id in the repository
- `new_node_id`
  - target canonical id for v1.0
- `new_category`
  - target canonical category id for v1.0
- `disposition`
  - one of `migrate`, `defer`, `legacy`
- `alias`
  - whether old id must remain loadable through alias compatibility
- `package_target`
  - recommended target package namespace
- `notes`
  - migration caveats or semantic comments

Rules:

- Any row marked `migrate` should eventually use the `new_node_id` as canonical.
- Any row marked `defer` stays outside the v1.0 main tree for now.
- Any row marked `legacy` is compatibility-only and must not be treated as the future canonical design.

## 2. Direct Migration Batch

These rows are the safest starting points because their semantics are already clear.

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `visualization.preview.geometry_viewer` | `output.preview.geometry_viewer` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_geometry` | `output.preview.preview_geometry` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_blocks` | `output.preview.preview_blocks` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_points` | `output.preview.preview_points` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_paths` | `output.preview.preview_curves` | `output.preview` | migrate | yes | `nodes.output.preview` | normalize “paths” to curve/curve-like preview language |
| `visualization.preview.preview_regions` | `output.preview.preview_regions` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_vectors` | `output.preview.preview_vectors` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_plane` | `output.preview.preview_plane` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_frame` | `output.preview.preview_frame` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_labels` | `output.preview.preview_labels` | `output.preview` | migrate | yes | `nodes.output.preview` | direct preview rename |
| `visualization.preview.preview_polygon_profiles` | `output.preview.preview_profiles` | `output.preview` | migrate | yes | `nodes.output.preview` | normalize to profile family |
| `visualization.preview.preview_surface_strip` | `output.preview.preview_surface_strip` | `output.preview` | migrate | yes | `nodes.output.preview` | keep until surface-strip type is redesigned |
| `visualization.preview.clear_all_previews` | `output.execute.clear_preview` | `output.execute` | migrate | yes | `nodes.output.execute` | execution-side cleanup, not passive preview |
| `visualization.execute.apply_changes` | `output.execute.apply_changes` | `output.execute` | migrate | yes | `nodes.output.execute` | direct execute rename |
| `visualization.execute.export_schematic` | `output.export.export_schematic` | `output.export` | migrate | yes | `nodes.output.export` | export split from execute |
| `visualization.debugging.value_monitor` | `output.debug.value_monitor` | `output.debug` | migrate | yes | `nodes.output.debug` | direct debug rename |
| `visualization.debugging.print_to_chat` | `output.debug.print_to_chat` | `output.debug` | migrate | yes | `nodes.output.debug` | direct debug rename |
| `visualization.debugging.execution_timer` | `output.debug.execution_timer` | `output.debug` | migrate | yes | `nodes.output.debug` | direct debug rename |
| `visualization.debugging.panel` | `output.debug.data_inspector` | `output.debug` | migrate | yes | `nodes.output.debug` | rename if panel survives as inspector-style debug UI |

## 3. Input Migration Ledger

## 3.1 `inputs.basic`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `inputs.basic.integer_input` | `input.numeric.integer` | `input.numeric` | migrate | yes | `nodes.input.numeric` | canonical scalar input |
| `inputs.basic.float_input` | `input.numeric.float` | `input.numeric` | migrate | yes | `nodes.input.numeric` | canonical scalar input |
| `inputs.basic.integer_slider` | `input.numeric.integer_slider` | `input.numeric` | migrate | yes | `nodes.input.numeric` | keep slider-specific variant |
| `inputs.basic.float_slider` | `input.numeric.float_slider` | `input.numeric` | migrate | yes | `nodes.input.numeric` | keep slider-specific variant |
| `inputs.basic.angle_slider` | `input.numeric.angle` | `input.numeric` | migrate | yes | `nodes.input.numeric` | canonical angle input |
| `inputs.basic.circular_angle` | `input.numeric.angle_picker` | `input.numeric` | migrate | yes | `nodes.input.numeric` | angle UI variant |
| `inputs.basic.boolean_toggle` | `input.numeric.boolean_toggle` | `input.numeric` | migrate | yes | `nodes.input.numeric` | stays in numeric/control input family |
| `inputs.basic.vector_input` | `reference.vectors.vector` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | semantically a vector constructor |
| `inputs.basic.coordinate_input` | `reference.points.point_from_coordinates` | `reference.points` | migrate | yes | `nodes.reference.points` | coordinate input is point construction |
| `inputs.basic.plane_selector` | `reference.planes.world_plane` | `reference.planes` | migrate | yes | `nodes.reference.planes` | if kept as standard plane picker |
| `inputs.basic.text_input` | `deferred.text.text_input` | deferred | defer | no | unchanged | text system excluded from v1.0 main tree |
| `inputs.basic.color_picker` | `deferred.input.color_picker` | deferred | defer | no | unchanged | color input not yet part of committed main taxonomy |

## 3.2 `inputs.minecraft`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `inputs.minecraft.player_position` | `input.context.player_position` | `input.context` | migrate | yes | `nodes.input.context` | direct context entry |
| `inputs.minecraft.player_look_at` | `input.context.player_look_direction` | `input.context` | migrate | yes | `nodes.input.context` | normalize naming to direction/context language |
| `inputs.minecraft.selected_block` | `world.selection.selected_block` | `world.selection` | migrate | yes | `nodes.world.selection` | canonical home is world selection |
| `inputs.minecraft.selected_region` | `world.selection.selected_region` | `world.selection` | migrate | yes | `nodes.world.selection` | canonical home is world selection |
| `inputs.minecraft.biome_at_player` | `world.read.biome_at_player` | `world.read` | migrate | yes | `nodes.world.read` | factual world read |
| `inputs.minecraft.current_time` | `input.context.current_time` | `input.context` | migrate | yes | `nodes.input.context` | retained as player/world context helper |
| `inputs.minecraft.dimension_info` | `input.context.dimension_info` | `input.context` | migrate | yes | `nodes.input.context` | retained as player/world context helper |
| `inputs.minecraft.selected_block_sequence` | `legacy.inputs.selected_block_sequence` | legacy | legacy | no | unchanged | deferred and displayed under `spatial.legacy` |
| `inputs.minecraft.selected_entity` | `legacy.inputs.selected_entity` | legacy | legacy | no | unchanged | entity selection excluded from v1 core and displayed under `spatial.legacy` |

## 3.3 `inputs.selectors`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `inputs.selectors.block_type_selector` | `input.type_selectors.block_type_selector` | `input.type_selectors` | migrate | yes | `nodes.input.type_selectors` | direct keep |
| `inputs.selectors.item_type_selector` | `deferred.input.item_type_selector` | deferred | defer | no | unchanged | inventory domain excluded |
| `inputs.selectors.entity_type_selector` | `deferred.input.entity_type_selector` | deferred | defer | no | unchanged | entity domain excluded |
| `inputs.selectors.effect_type_selector` | `deferred.input.effect_type_selector` | deferred | defer | no | unchanged | not part of main v1.0 scope |
| `inputs.selectors.sound_event_selector` | `deferred.input.sound_event_selector` | deferred | defer | no | unchanged | not part of main v1.0 scope |

## 3.4 `inputs.sources`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `inputs.sources.create_list` | `math.list_sequence.create_list` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | data constructor, not input |
| `inputs.sources.text_panel` | `deferred.text.text_panel` | deferred | defer | no | unchanged | text workflow excluded |
| `inputs.sources.file_path` | `deferred.io.file_path` | deferred | defer | no | unchanged | file workflow excluded |

## 4. Math and Logic Migration Ledger

## 4.1 `math.basic`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `math.basic.addition` | `math.scalar_math.addition` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.subtraction` | `math.scalar_math.subtraction` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.multiplication` | `math.scalar_math.multiplication` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.division` | `math.scalar_math.division` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.modulus` | `math.scalar_math.modulus` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.absolute` | `math.scalar_math.absolute` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.clamp` | `math.scalar_math.clamp` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.remap` | `math.scalar_math.remap` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.round` | `math.scalar_math.round` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.floor` | `math.scalar_math.floor` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.ceiling` | `math.scalar_math.ceiling` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.min` | `math.scalar_math.min` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.max` | `math.scalar_math.max` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.power` | `math.scalar_math.power` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | direct rename |
| `math.basic.logarithm` | `math.scalar_math.logarithm` | `math.scalar_math` | migrate | yes | `nodes.math.scalar_math` | retained as scalar math utility |
| `math.basic.math_range` | `math.list_sequence.range` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | sequence producer, not scalar op |
| `math.basic.math_series` | `deferred.math.math_series` | deferred | defer | no | unchanged | keep deferred until semantics are clarified against range/sequence |

## 4.2 `math.logic`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `math.logic.and` | `math.logic.and` | `math.logic` | migrate | no | `nodes.math.logic` | already canonical enough except package |
| `math.logic.or` | `math.logic.or` | `math.logic` | migrate | no | `nodes.math.logic` | already canonical enough except package |
| `math.logic.not` | `math.logic.not` | `math.logic` | migrate | no | `nodes.math.logic` | already canonical enough except package |
| `math.logic.if` | `math.logic.if` | `math.logic` | migrate | no | `nodes.math.logic` | already canonical enough except package |
| `math.logic.xor` | `math.logic.xor` | `math.logic` | migrate | no | `nodes.math.logic` | retained under logic |
| `math.logic.equals` | `math.compare.equals` | `math.compare` | migrate | yes | `nodes.math.compare` | comparison family |
| `math.logic.not_equals` | `math.compare.not_equals` | `math.compare` | migrate | yes | `nodes.math.compare` | comparison family |
| `math.logic.less_than` | `math.compare.less_than` | `math.compare` | migrate | yes | `nodes.math.compare` | comparison family |
| `math.logic.less_than_or_equal` | `math.compare.less_than_or_equal` | `math.compare` | migrate | yes | `nodes.math.compare` | comparison family |
| `math.logic.greater_than` | `math.compare.greater_than` | `math.compare` | migrate | yes | `nodes.math.compare` | comparison family |
| `math.logic.greater_than_or_equal` | `math.compare.greater_than_or_equal` | `math.compare` | migrate | yes | `nodes.math.compare` | comparison family |
| `math.logic.select_item` | `math.logic.switch` | `math.logic` | migrate | yes | `nodes.math.logic` | closest v1.0 semantic equivalent |

## 4.3 `math.randomness`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `math.randomness.random_number` | `math.random.random_number` | `math.random` | migrate | yes | `nodes.math.random` | direct rename |
| `math.randomness.noise` | `math.random.noise_sample` | `math.random` | migrate | yes | `nodes.math.random` | normalize naming |
| `math.randomness.random_list_item` | `math.random.random_list_item` | `math.random` | migrate | yes | `nodes.math.random` | general random helper |
| `math.randomness.random_vector` | `deferred.math.random_vector` | deferred | defer | no | unchanged | spatial/random hybrid, defer until reference random API is designed |

## 4.4 `math.trigonometry`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `math.trigonometry.sine` | `math.trigonometry.sin` | `math.trigonometry` | migrate | yes | `nodes.math.trigonometry` | normalize naming |
| `math.trigonometry.cosine` | `math.trigonometry.cos` | `math.trigonometry` | migrate | yes | `nodes.math.trigonometry` | normalize naming |
| `math.trigonometry.tangent` | `math.trigonometry.tan` | `math.trigonometry` | migrate | yes | `nodes.math.trigonometry` | normalize naming |
| `math.trigonometry.arc_sin` | `math.trigonometry.asin` | `math.trigonometry` | migrate | yes | `nodes.math.trigonometry` | normalize naming |
| `math.trigonometry.arc_cos` | `math.trigonometry.acos` | `math.trigonometry` | migrate | yes | `nodes.math.trigonometry` | normalize naming |
| `math.trigonometry.arc_tan` | `math.trigonometry.atan2` | `math.trigonometry` | migrate | yes | `nodes.math.trigonometry` | if node is two-argument keep `atan2`, otherwise rename to `atan` during implementation review |
| `math.trigonometry.degrees_to_radians` | `math.trigonometry.degrees_to_radians` | `math.trigonometry` | migrate | no | `nodes.math.trigonometry` | already canonical enough |
| `math.trigonometry.radians_to_degrees` | `math.trigonometry.radians_to_degrees` | `math.trigonometry` | migrate | no | `nodes.math.trigonometry` | already canonical enough |
| `math.trigonometry.pi` | `deferred.math.pi_constant` | deferred | defer | no | unchanged | constant node not in current v1.0 essential set |

## 4.5 `math.vector`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `math.vector.construct_vector` | `reference.vectors.vector` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector constructor |
| `math.vector.cross_product` | `reference.vectors.cross_product` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector operator stays with vectors |
| `math.vector.dot_product` | `reference.vectors.dot_product` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector operator stays with vectors |
| `math.vector.normalize_vector` | `reference.vectors.normalize_vector` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector operator stays with vectors |
| `math.vector.vector_length` | `reference.vectors.vector_length` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | scalar-from-vector utility |
| `math.vector.vector_addition` | `reference.vectors.vector_addition` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector family |
| `math.vector.vector_subtraction` | `reference.vectors.vector_subtraction` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector family |
| `math.vector.vector_scalar_multiply` | `reference.vectors.vector_scalar_multiply` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector family |
| `math.vector.vector_scalar_divide` | `reference.vectors.vector_scalar_divide` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector family |
| `math.vector.construct_coordinate` | `reference.points.point_from_coordinates` | `reference.points` | migrate | yes | `nodes.reference.points` | unify coordinate/point construction |
| `math.vector.deconstruct_coordinate` | `reference.points.deconstruct_point` | `reference.points` | migrate | yes | `nodes.reference.points` | point decomposition |
| `math.vector.midpoint` | `reference.points.mid_point` | `reference.points` | migrate | yes | `nodes.reference.points` | direct reference-point helper |
| `math.vector.distance` | `reference.points.distance_between_points` | `reference.points` | migrate | yes | `nodes.reference.points` | keep near point/reference semantics |
| `math.vector.construct_plane` | `reference.planes.construct_plane` | `reference.planes` | migrate | yes | `nodes.reference.planes` | direct plane constructor |
| `math.vector.construct_plane_from_points` | `reference.planes.plane_from_points` | `reference.planes` | migrate | yes | `nodes.reference.planes` | direct plane constructor |
| `math.vector.deconstruct_vector` | `reference.vectors.deconstruct_vector` | `reference.vectors` | migrate | yes | `nodes.reference.vectors` | vector decomposition |
| `math.vector.rotate_vector` | `transform.orientation.rotate_vector` | `transform.orientation` | migrate | yes | `nodes.transform.orientation` | first true transform-style helper in old vector group |

## 5. World Migration Ledger

## 5.1 `world.query`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `world.query.get_block` | `world.read.get_block` | `world.read` | migrate | yes | `nodes.world.read` | factual retrieval |
| `world.query.get_blocks_in_region` | `world.read.get_blocks_in_region` | `world.read` | migrate | yes | `nodes.world.read` | factual retrieval |
| `world.query.find_blocks` | `world.read.find_blocks` | `world.read` | migrate | yes | `nodes.world.read` | factual retrieval |
| `world.query.get_biome` | `world.read.get_biome` | `world.read` | migrate | yes | `nodes.world.read` | factual retrieval |
| `world.query.get_light_level` | `world.query.get_light_level` | `world.query` | migrate | no | `nodes.world.query` | already canonical enough |
| `world.query.get_fluid_level` | `world.query.get_fluid_level` | `world.query` | migrate | no | `nodes.world.query` | already canonical enough |
| `world.query.get_entity` | `deferred.world.get_entity` | deferred | defer | no | unchanged | entity domain excluded |
| `world.query.get_entities_in_region` | `deferred.world.get_entities_in_region` | deferred | defer | no | unchanged | entity domain excluded |

## 5.2 `world.modification`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `world.modification.set_block` | `world.write.set_block` | `world.write` | migrate | yes | `nodes.world.write` | low-level write |
| `world.modification.set_blocks` | `world.write.set_blocks` | `world.write` | migrate | yes | `nodes.world.write` | low-level write |
| `world.modification.fill_region` | `world.write.fill_region` | `world.write` | migrate | yes | `nodes.world.write` | low-level write |
| `world.modification.replace_blocks` | `world.write.replace_blocks` | `world.write` | migrate | yes | `nodes.world.write` | low-level write |
| `world.modification.clone_region` | `world.write.clone_region` | `world.write` | migrate | yes | `nodes.world.write` | low-level write |
| `world.modification.remove_blocks` | `world.write.clear_region` | `world.write` | migrate | yes | `nodes.world.write` | canonical v1.0 wording prefers clear semantics |
| `world.modification.material_mapper` | `material.basic_assignment.replace_material` | `material.basic_assignment` | migrate | yes | `nodes.material.basic_assignment` | semantic move out of world layer |

## 6. Pattern Migration Ledger

## 6.1 `spatial.arrays`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `spatial.arrays.linear_array` | `pattern.linear.linear_array` | `pattern.linear` | migrate | yes | `nodes.pattern.linear` | direct semantic move |
| `spatial.arrays.grid_array` | `pattern.grid.grid_array` | `pattern.grid` | migrate | yes | `nodes.pattern.grid` | direct semantic move |
| `spatial.arrays.polar_array` | `pattern.radial.polar_array` | `pattern.radial` | migrate | yes | `nodes.pattern.radial` | direct semantic move |
| `spatial.arrays.populate_region` | `pattern.surface_volume_distribution.populate_region` | `pattern.surface_volume_distribution` | migrate | yes | `nodes.pattern.surface_volume_distribution` | distribution family |

## 7. Reference Migration Ledger

## 7.1 `spatial.points`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `spatial.points.block_to_point` | `reference.points.point_from_block` | `reference.points` | migrate | yes | `nodes.reference.points` | point construction |
| `spatial.points.point_between_two_points` | `reference.points.mid_point` | `reference.points` | migrate | yes | `nodes.reference.points` | may coexist with weighted midpoint later |
| `spatial.points.point_along_vector` | `reference.points.point_along_vector` | `reference.points` | migrate | yes | `nodes.reference.points` | reference construction |
| `spatial.points.offset_coordinate` | `transform.basic_transforms.move_point` | `transform.basic_transforms` | migrate | yes | `nodes.transform.basic_transforms` | point transform |
| `spatial.points.offset_coordinates` | `transform.basic_transforms.move_points` | `transform.basic_transforms` | migrate | yes | `nodes.transform.basic_transforms` | point-list transform |
| `spatial.points.rotate_coordinates` | `transform.basic_transforms.rotate_points` | `transform.basic_transforms` | migrate | yes | `nodes.transform.basic_transforms` | point-list transform |
| `spatial.points.scale_coordinates` | `transform.basic_transforms.scale_points` | `transform.basic_transforms` | migrate | yes | `nodes.transform.basic_transforms` | point-list transform |
| `spatial.points.mirror_coordinates` | `transform.basic_transforms.mirror_points` | `transform.basic_transforms` | migrate | yes | `nodes.transform.basic_transforms` | point-list transform |
| `spatial.points.project_point_to_plane` | `transform.orientation.project_to_plane` | `transform.orientation` | migrate | yes | `nodes.transform.orientation` | orientation/projection semantic |
| `spatial.points.points_to_path` | `geometry.curves.curve_from_points` | `geometry.curves` | migrate | yes | `nodes.geometry.curves` | canonical curve constructor |
| `spatial.points.path_to_points` | `geometry.curves.divide_curve_to_points` | `geometry.curves` | migrate | yes | `nodes.geometry.curves` | convert curve back into point sequence |
| `spatial.points.distance_point_to_plane` | `reference.planes.distance_point_to_plane` | `reference.planes` | migrate | yes | `nodes.reference.planes` | plane/reference query |
| `spatial.points.snap_point_to_block` | `world.selection.snap_point_to_block` | `world.selection` | migrate | yes | `nodes.world.selection` | world grid snapping helper |
| `spatial.points.snap_point_list_to_blocks` | `world.selection.snap_points_to_blocks` | `world.selection` | migrate | yes | `nodes.world.selection` | world grid snapping helper |
| `spatial.points.point_to_block_if_grid` | `world.selection.point_to_block_if_grid` | `world.selection` | migrate | yes | `nodes.world.selection` | world conversion helper |
| `spatial.points.is_grid_point` | `world.query.is_grid_point` | `world.query` | migrate | yes | `nodes.world.query` | environment/grid validity helper |
| `spatial.points.filter_grid_points` | `world.query.filter_grid_points` | `world.query` | migrate | yes | `nodes.world.query` | environment/grid validity helper |
| `spatial.points.randomize_coordinates` | `deferred.transform.randomize_points` | deferred | defer | no | unchanged | noise/random transform not in first v1.0 core set |

## 7.2 `math.vector` and `inputs.basic` rows moved into `reference.*`

These rows are already listed above and are part of the same reference migration batch:

- `inputs.basic.vector_input`
- `inputs.basic.coordinate_input`
- `inputs.basic.plane_selector`
- `math.vector.construct_vector`
- `math.vector.construct_coordinate`
- `math.vector.construct_plane`
- `math.vector.construct_plane_from_points`
- `math.vector.midpoint`
- `math.vector.distance`

## 8. Geometry Migration Ledger

## 8.1 `spatial.construct`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `spatial.construct.box_center_size` | `geometry.primitives.box` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | constructor variant becomes box primitive |
| `spatial.construct.box_corner_size` | `geometry.primitives.box_from_corner_size` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | keep explicit variant if separate contract is useful |
| `spatial.construct.box_corners` | `geometry.primitives.box_from_corners` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | keep explicit variant if separate contract is useful |
| `spatial.construct.sphere_by_center_radius` | `geometry.primitives.sphere` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | constructor variant becomes sphere primitive |
| `spatial.construct.sphere_by_diameter` | `geometry.primitives.sphere_from_diameter` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | keep explicit variant if separate contract is useful |
| `spatial.construct.cylinder_by_axis_radius` | `geometry.primitives.cylinder` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | canonical cylinder primitive |
| `spatial.construct.cone_by_base_apex_radius` | `geometry.primitives.cone` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | canonical cone primitive |
| `spatial.construct.ellipsoid_by_center_radii` | `geometry.primitives.ellipsoid` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | canonical ellipsoid primitive |
| `spatial.construct.octahedron_by_center_size` | `geometry.primitives.octahedron` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | retained as primitive family member |
| `spatial.construct.tetrahedron_by_center_edge` | `geometry.primitives.tetrahedron` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | retained as primitive family member |
| `spatial.construct.rectangle_on_plane` | `geometry.profiles.rectangle_profile` | `geometry.profiles` | migrate | yes | `nodes.geometry.profiles` | canonical 2D profile |
| `spatial.construct.regular_polygon_on_plane` | `geometry.profiles.polygon_profile` | `geometry.profiles` | migrate | yes | `nodes.geometry.profiles` | canonical 2D profile |
| `spatial.construct.polygon_by_points` | `geometry.profiles.custom_profile` | `geometry.profiles` | migrate | yes | `nodes.geometry.profiles` | point-defined profile |
| `spatial.construct.prism_by_profile_vector` | `geometry.solids.extrude_profile` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | semantic extrude, not standalone primitive |
| `spatial.construct.prism_by_base_points_vector` | `geometry.solids.extrude_profile_from_points` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | semantic extrude, not standalone primitive |

## 8.2 `spatial.modeling`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `spatial.modeling.extrude_profile` | `geometry.solids.extrude` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | canonical solid generation |
| `spatial.modeling.extrude_point_list` | `geometry.solids.extrude_from_points` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | likely folded into extrude later |
| `spatial.modeling.extrude_box_face` | `geometry.solids.extrude_box_face` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | face-based solid generation |
| `spatial.modeling.loft_profiles` | `geometry.solids.loft` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | canonical loft |
| `spatial.modeling.loft_point_lists` | `geometry.solids.loft_from_points` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | point-derived loft |
| `spatial.modeling.sweep_profile_along_path` | `geometry.solids.sweep` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | canonical sweep |
| `spatial.modeling.sweep_point_list_along_path` | `geometry.solids.sweep_from_points` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | point-derived sweep |
| `spatial.modeling.resample_polygon_profile` | `geometry.profiles.resample_profile` | `geometry.profiles` | migrate | yes | `nodes.geometry.profiles` | profile utility |
| `spatial.modeling.surface_strip_to_geometry` | `geometry.solids.surface_strip_to_geometry` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | temporary bridge until surface toolkit stabilizes |
| `spatial.modeling.push_pull_box_face` | `geometry.solids.push_pull_face` | `geometry.solids` | migrate | yes | `nodes.geometry.solids` | semantic modeling operation |
| `spatial.modeling.twist_point_list` | `transform.deformations.twist` | `transform.deformations` | migrate | yes | `nodes.transform.deformations` | true deformation node |

## 8.3 `spatial.analysis`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `spatial.analysis.bounding_box` | `geometry.boolean.bounding_box` | `geometry.boolean` | migrate | yes | `nodes.geometry.boolean` | geometry bounds helper pending better home |
| `spatial.analysis.geometry_bounds` | `geometry.boolean.geometry_bounds` | `geometry.boolean` | migrate | yes | `nodes.geometry.boolean` | temporary home until dedicated analysis layer exists |
| `spatial.analysis.geometry_info` | `legacy.geometry.geometry_info` | legacy | legacy | no | unchanged | currently deferred and displayed under `spatial.legacy` |
| `spatial.analysis.box_face_to_plane` | `reference.planes.block_face_plane` | `reference.planes` | migrate | yes | `nodes.reference.planes` | plane extraction |
| `spatial.analysis.face_center_frame` | `reference.frames.frame_from_face` | `reference.frames` | migrate | yes | `nodes.reference.frames` | frame extraction |
| `spatial.analysis.face_edge_to_path` | `geometry.curves.edge_to_curve` | `geometry.curves` | migrate | yes | `nodes.geometry.curves` | curve extraction |
| `spatial.analysis.box_face_boundary_path` | `geometry.curves.face_boundary_curve` | `geometry.curves` | migrate | yes | `nodes.geometry.curves` | curve extraction |
| `spatial.analysis.deconstruct_box_geometry` | `geometry.primitives.deconstruct_box` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_sphere` | `geometry.primitives.deconstruct_sphere` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_cylinder` | `geometry.primitives.deconstruct_cylinder` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_cone` | `geometry.primitives.deconstruct_cone` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_ellipsoid` | `geometry.primitives.deconstruct_ellipsoid` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_octahedron` | `geometry.primitives.deconstruct_octahedron` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_tetrahedron` | `geometry.primitives.deconstruct_tetrahedron` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_prism` | `geometry.primitives.deconstruct_prism` | `geometry.primitives` | migrate | yes | `nodes.geometry.primitives` | primitive decomposition |
| `spatial.analysis.deconstruct_polygon_profile` | `geometry.profiles.deconstruct_profile` | `geometry.profiles` | migrate | yes | `nodes.geometry.profiles` | profile decomposition |
| `spatial.analysis.deconstruct_box_face` | `reference.points.deconstruct_face` | `reference.points` | migrate | yes | `nodes.reference.points` | face reference extraction |
| `spatial.analysis.deconstruct_face_edge` | `reference.points.deconstruct_edge` | `reference.points` | migrate | yes | `nodes.reference.points` | edge reference extraction |
| `spatial.analysis.closest_point` | `reference.points.closest_point` | `reference.points` | migrate | yes | `nodes.reference.points` | spatial reference query |
| `spatial.analysis.point_list_center` | `reference.points.point_list_center` | `reference.points` | migrate | yes | `nodes.reference.points` | reference query |
| `spatial.analysis.point_list_bounds` | `reference.points.point_list_bounds` | `reference.points` | migrate | yes | `nodes.reference.points` | reference query |
| `spatial.analysis.get_points_in_region` | `world.read.get_points_in_region` | `world.read` | migrate | yes | `nodes.world.read` | world-derived sampling helper |
| `spatial.analysis.is_point_in_region` | `world.query.is_point_in_region` | `world.query` | migrate | yes | `nodes.world.query` | world/query helper |
| `spatial.analysis.get_box_corner` | `reference.points.get_box_corner` | `reference.points` | migrate | yes | `nodes.reference.points` | reference extractor |
| `spatial.analysis.get_box_face` | `reference.points.get_box_face` | `reference.points` | migrate | yes | `nodes.reference.points` | reference extractor |
| `spatial.analysis.get_face_edge` | `reference.points.get_face_edge` | `reference.points` | migrate | yes | `nodes.reference.points` | reference extractor |
| `spatial.analysis.offset_box_face` | `transform.basic_transforms.offset_face` | `transform.basic_transforms` | migrate | yes | `nodes.transform.basic_transforms` | modeling helper through transform semantics |
| `spatial.analysis.inset_box_face` | `transform.basic_transforms.inset_face` | `transform.basic_transforms` | migrate | yes | `nodes.transform.basic_transforms` | modeling helper through transform semantics |
| `spatial.analysis.sample_sphere_surface` | `pattern.surface_volume_distribution.sample_surface` | `pattern.surface_volume_distribution` | migrate | yes | `nodes.pattern.surface_volume_distribution` | surface sampling |
| `spatial.analysis.scatter_on_sphere_surface` | `pattern.surface_volume_distribution.surface_scatter` | `pattern.surface_volume_distribution` | migrate | yes | `nodes.pattern.surface_volume_distribution` | surface distribution |
| `spatial.analysis.select_sphere_band_sector` | `legacy.geometry.select_sphere_band_sector` | legacy | legacy | no | unchanged | deferred and displayed under `spatial.legacy` |
| `spatial.analysis.sphere_uv` | `legacy.geometry.sphere_uv` | legacy | legacy | no | unchanged | deferred and displayed under `spatial.legacy` |
| `spatial.analysis.sphere_surface_frame` | `reference.frames.frame_along_surface` | `reference.frames` | migrate | yes | `nodes.reference.frames` | specialized but still reference-oriented |
| `spatial.analysis.sphere_point_info` | `legacy.geometry.sphere_point_info` | legacy | legacy | no | unchanged | deferred and displayed under `spatial.legacy` |
| `spatial.analysis.deconstruct_surface_strip` | `legacy.geometry.deconstruct_surface_strip` | legacy | legacy | no | unchanged | keep until surface-strip model is stabilized; displayed under `spatial.legacy` |

## 9. Execution Bridge and Legacy Geometry Ledger

## 9.1 `spatial.voxel`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `spatial.voxel.geometry_to_blocks` | `output.execute.bake_geometry_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | canonical bridge from geometry to placements |
| `spatial.voxel.surface_strip_to_blocks` | `output.execute.bake_surface_strip_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | transitional bridge |
| `spatial.voxel.box_geometry_voxelizer` | `output.execute.bake_box_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.sphere_geometry_voxelizer` | `output.execute.bake_sphere_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.cylinder_geometry_voxelizer` | `output.execute.bake_cylinder_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.cone_geometry_voxelizer` | `output.execute.bake_cone_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.ellipsoid_geometry_voxelizer` | `output.execute.bake_ellipsoid_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.octahedron_geometry_voxelizer` | `output.execute.bake_octahedron_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.tetrahedron_geometry_voxelizer` | `output.execute.bake_tetrahedron_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.torus_geometry_voxelizer` | `output.execute.bake_torus_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.prism_geometry_voxelizer` | `output.execute.bake_prism_to_blocks` | `output.execute` | migrate | yes | `nodes.output.execute` | may later collapse into generic bake |
| `spatial.voxel.union_coords` | `legacy.spatial.union_coords` | legacy | legacy | no | unchanged | keep only until geometry boolean-to-placement bridge is redesigned |
| `spatial.voxel.intersection_coords` | `legacy.spatial.intersection_coords` | legacy | legacy | no | unchanged | keep only until geometry boolean-to-placement bridge is redesigned |
| `spatial.voxel.difference_coords` | `legacy.spatial.difference_coords` | legacy | legacy | no | unchanged | keep only until geometry boolean-to-placement bridge is redesigned |

## 9.2 `spatial.generators`

These are historical direct block-output nodes.

They are not valid v1.0 canonical geometry taxonomy.

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `spatial.generators.line_blocks` | `legacy.spatial.line_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.rectangle_blocks` | `legacy.spatial.rectangle_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.box_blocks` | `legacy.spatial.box_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.circle_sphere_blocks` | `legacy.spatial.circle_sphere_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.cylinder_blocks` | `legacy.spatial.cylinder_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.polyline_blocks` | `legacy.spatial.polyline_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.curve_blocks` | `legacy.spatial.curve_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.ellipsoid_blocks` | `legacy.spatial.ellipsoid_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.cone_blocks` | `legacy.spatial.cone_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.torus_blocks` | `legacy.spatial.torus_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.octahedron_blocks` | `legacy.spatial.octahedron_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.tetrahedron_blocks` | `legacy.spatial.tetrahedron_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.triangular_pyramid_blocks` | `legacy.spatial.triangular_pyramid_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.triangular_prism_blocks` | `legacy.spatial.triangular_prism_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.arc_blocks` | `legacy.spatial.arc_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.ellipse_blocks` | `legacy.spatial.ellipse_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.region_box_blocks` | `legacy.spatial.region_box_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.regular_polygon_blocks` | `legacy.spatial.regular_polygon_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.semicircle_blocks` | `legacy.spatial.semicircle_blocks` | legacy | legacy | no | unchanged | compatibility-only |
| `spatial.generators.star_blocks` | `legacy.spatial.star_blocks` | legacy | legacy | no | unchanged | compatibility-only |

## 10. Control and Data Migration Ledger

## 10.1 `control.flow`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `control.flow.compare` | `math.compare.compare` | `math.compare` | migrate | yes | `nodes.math.compare` | reusable comparison node |
| `control.flow.branch` | `math.logic.if` | `math.logic` | migrate | yes | `nodes.math.logic` | branch folds into canonical conditional node |
| `control.flow.switch_select` | `math.logic.switch` | `math.logic` | migrate | yes | `nodes.math.logic` | direct logic move |
| `control.flow.for_each` | `deferred.control.for_each` | deferred | defer | no | unchanged | execution-flow feature, not v1.0 main tree |
| `control.flow.geometry_gate` | `deferred.control.geometry_gate` | deferred | defer | no | unchanged | execution-flow feature |
| `control.flow.geometry_merge` | `deferred.control.geometry_merge` | deferred | defer | no | unchanged | execution-flow feature |
| `control.flow.geometry_passthrough` | `deferred.control.geometry_passthrough` | deferred | defer | no | unchanged | execution-flow feature |
| `control.flow.geometry_switch` | `deferred.control.geometry_switch` | deferred | defer | no | unchanged | execution-flow feature |

## 10.2 `data.lists` and `data.sequence`

| old_node_id | new_node_id | new_category | disposition | alias | package_target | notes |
|---|---|---|---|---|---|---|
| `data.lists.list_length` | `math.list_sequence.list_length` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | core sequence helper |
| `data.lists.get_item` | `math.list_sequence.get_item` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | core sequence helper |
| `data.lists.sub_list` | `math.list_sequence.slice_list` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | normalize naming |
| `data.lists.combine_lists` | `math.list_sequence.zip_lists` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | if behavior is zip-like; otherwise review during implementation |
| `data.sequence.range` | `math.list_sequence.range` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | direct move |
| `data.sequence.repeat` | `math.list_sequence.repeat_list` | `math.list_sequence` | migrate | yes | `nodes.math.list_sequence` | direct move |
| `data.sequence.series` | `deferred.data.series` | deferred | defer | no | unchanged | broader data-sequence feature |
| `data.lists.dispatch_list` | `deferred.data.dispatch_list` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.filter_list` | `deferred.data.filter_list` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.flatten_list` | `deferred.data.flatten_list` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.group_list` | `deferred.data.group_list` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.insert_item` | `deferred.data.insert_item` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.remove_item` | `deferred.data.remove_item` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.reverse_list` | `deferred.data.reverse_list` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.set_item` | `deferred.data.set_item` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.shuffle_list` | `deferred.data.shuffle_list` | deferred | defer | no | unchanged | broader data tooling |
| `data.lists.sort_list` | `deferred.data.sort_list` | deferred | defer | no | unchanged | broader data tooling |

## 11. Deferred Domain Ledger

These domains are intentionally out of scope for the v1.0 main tree.

They should not be migrated into canonical v1.0 categories during the current refactor.

| domain | disposition | notes |
|---|---|---|
| `animation.*` | defer | whole domain excluded from v1.0 main tree |
| `flora.*` | defer | whole domain excluded from v1.0 main tree |
| `world.nbt.*` | defer | excluded by scope |
| `world.inventory.*` | defer | excluded by scope |
| `world.entity.*` | defer | excluded by scope |
| `world.interaction.*` | defer | excluded by scope |
| `utilities.*` | defer | workflow/editor system, not core v1.0 taxonomy |
| `data.text.*` | defer | text subsystem outside current main tree |
| `data.conversion.*` | defer | conversion subsystem outside current main tree |
| `spatial.sdf.*` | defer | explicitly postponed |

## 12. Migration Order

Execute in this order:

1. `visualization.*` -> `output.*`
2. `world.query` and `world.modification`
3. `inputs.*`
4. `math.basic`, `math.logic`, `math.randomness`, `math.trigonometry`
5. `math.vector`
6. `spatial.arrays`
7. `spatial.points`
8. `spatial.construct`
9. `spatial.modeling`
10. `spatial.analysis`
11. `spatial.voxel`
12. leave `spatial.generators` as legacy compatibility

## 13. Hard Constraints for Implementation

During code migration:

- do not create new canonical ids under `spatial.*`
- do not create new canonical ids under `visualization.*`
- do not create new canonical ids under `inputs.*`
- do not silently migrate deferred domains into the main v1.0 tree
- do preserve old ids through alias compatibility whenever a canonical id changes
- do update package path, `@NodeInfo(id/category)`, and `BaseNode` type id together

## 14. Next Step After This Ledger

After this ledger, the next implementation artifact should be:

- an alias plan for `NodeRegistry`

That plan should batch aliases by migration wave so code changes can land incrementally without breaking graph loading.
