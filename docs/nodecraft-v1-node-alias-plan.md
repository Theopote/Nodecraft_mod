# NodeCraft v1.0 Node Alias Plan

> **Compatibility history / migration seed — not live taxonomy.**  
> Canonical ids today: code + [`NODE_LIBRARY.md`](./NODE_LIBRARY.md).  
> Runtime load of old ids: V0→V1 migration (`architecture/graph-format-version.md`).  
> Docs authority: [`architecture/docs-authority.md`](./architecture/docs-authority.md).

This document defined how node-id alias compatibility should be expanded during the
NodeCraft v1.0 migration. It still informs the migration manifest generation path; it
must not be read as the current node library.

It exists to prevent graph-loading regressions while canonical ids are being moved from legacy taxonomy to the v1.0 taxonomy.

## 1. Purpose

Aliases exist for compatibility, not taxonomy.

They are required because old assets may still contain:

- saved graph node ids
- clipboard payload node ids
- history snapshot node ids
- duplicate-node intermediate ids
- outdated documentation references

The migration must preserve loading compatibility while ensuring new saves write only canonical ids.

## 2. Scope

This plan applies to:

- `src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java`

It assumes the canonical category and node-id targets are defined by:

- [nodecraft-v1-category-id-guidelines.md](/f:/development/NC/nodecraft/docs/nodecraft-v1-category-id-guidelines.md)
- [nodecraft-v1-category-migration-map.md](/f:/development/NC/nodecraft/docs/nodecraft-v1-category-migration-map.md)
- [nodecraft-v1-node-migration-ledger.md](/f:/development/NC/nodecraft/docs/nodecraft-v1-node-migration-ledger.md)

## 3. Alias Rules

### 3.1 One-way only

Aliases must always resolve:

`legacy id -> canonical id`

Never the reverse.

### 3.2 Canonical write paths must stay clean

The following paths must continue writing canonical ids only:

- graph save
- clipboard export
- history snapshots
- duplicate-node flows

Alias support is for loading and resolution only.

### 3.3 Do not use aliasing as a substitute for proper migration

If a node has a new canonical id:

- update package path
- update `@NodeInfo(id = ...)`
- update `BaseNode` type id
- then add the alias

Do not leave the old id in place and rely on category remapping forever.

### 3.4 Alias entries should be grouped by migration wave

Do not add the entire migration ledger into `NodeRegistry` in one pass.

Add aliases in waves that match code migration waves.

That keeps each implementation step reviewable and reversible.

## 4. Alias Entry Types

The v1.0 migration should use three alias types.

### 4.1 Renamed canonical alias

Used when a node keeps the same behavior but gets a new canonical id.

Example:

```text
visualization.preview.geometry_viewer -> output.preview.geometry_viewer
```

### 4.2 Legacy normalization alias

Used when an old malformed or compact id must normalize to the old canonical id before any v1.0 move.

Example:

```text
spatial.generators.torusblocks -> spatial.generators.torus_blocks
```

These should stay only where old malformed ids are known to exist.

### 4.3 Chained historic alias

Used when an old id already moved once before v1.0 and now moves again.

Example shape:

```text
spatial.generators.sphere_by_center_radius -> geometry.primitives.sphere
```

Important:

- `NodeRegistry` should resolve directly to the final canonical id
- do not rely on alias-to-alias chains at runtime if they can be flattened safely

## 5. Resolution Rules

When alias tables grow, `NodeRegistry` resolution should obey:

1. lowercase normalize input id
2. resolve explicit alias to final canonical id
3. if alias maps to another alias target, flatten during table construction or resolve until stable
4. instantiate only by final canonical id

Recommendation:

- keep alias maps flattened ahead of time whenever possible

## 6. Migration Waves

Aliases should be added in the same order as code migration.

## Wave 1: `visualization.*` -> `output.*`

This is the safest wave and should land first.

Required aliases:

- `visualization.preview.geometry_viewer` -> `output.preview.geometry_viewer`
- `visualization.preview.preview_geometry` -> `output.preview.preview_geometry`
- `visualization.preview.preview_blocks` -> `output.preview.preview_blocks`
- `visualization.preview.preview_points` -> `output.preview.preview_points`
- `visualization.preview.preview_paths` -> `output.preview.preview_curves`
- `visualization.preview.preview_regions` -> `output.preview.preview_regions`
- `visualization.preview.preview_vectors` -> `output.preview.preview_vectors`
- `visualization.preview.preview_plane` -> `output.preview.preview_plane`
- `visualization.preview.preview_frame` -> `output.preview.preview_frame`
- `visualization.preview.preview_labels` -> `output.preview.preview_labels`
- `visualization.preview.preview_polygon_profiles` -> `output.preview.preview_profiles`
- `visualization.preview.preview_surface_strip` -> `output.preview.preview_surface_strip`
- `visualization.preview.clear_all_previews` -> `output.execute.clear_preview`
- `visualization.execute.apply_changes` -> `output.execute.apply_changes`
- `visualization.execute.export_schematic` -> `output.export.export_schematic`
- `visualization.debugging.value_monitor` -> `output.debug.value_monitor`
- `visualization.debugging.print_to_chat` -> `output.debug.print_to_chat`
- `visualization.debugging.execution_timer` -> `output.debug.execution_timer`
- `visualization.debugging.panel` -> `output.debug.data_inspector`

## Wave 2: `world.query` and `world.modification`

Required aliases:

- `world.query.get_block` -> `world.read.get_block`
- `world.query.get_blocks_in_region` -> `world.read.get_blocks_in_region`
- `world.query.find_blocks` -> `world.read.find_blocks`
- `world.query.get_biome` -> `world.read.get_biome`
- `world.modification.set_block` -> `world.write.set_block`
- `world.modification.set_blocks` -> `world.write.set_blocks`
- `world.modification.fill_region` -> `world.write.fill_region`
- `world.modification.replace_blocks` -> `world.write.replace_blocks`
- `world.modification.clone_region` -> `world.write.clone_region`
- `world.modification.remove_blocks` -> `world.write.clear_region`
- `world.modification.material_mapper` -> `material.basic_assignment.replace_material`

No alias needed in this wave for nodes that remain canonical under the same id:

- `world.query.get_light_level`
- `world.query.get_fluid_level`

## Wave 3: `inputs.*`

Required aliases:

- `inputs.basic.integer_input` -> `input.numeric.integer`
- `inputs.basic.float_input` -> `input.numeric.float`
- `inputs.basic.integer_slider` -> `input.numeric.integer_slider`
- `inputs.basic.float_slider` -> `input.numeric.float_slider`
- `inputs.basic.angle_slider` -> `input.numeric.angle`
- `inputs.basic.circular_angle` -> `input.numeric.angle_picker`
- `inputs.basic.boolean_toggle` -> `input.numeric.boolean_toggle`
- `inputs.basic.vector_input` -> `reference.vectors.vector`
- `inputs.basic.coordinate_input` -> `reference.points.point_from_coordinates`
- `inputs.basic.plane_selector` -> `reference.planes.world_plane`
- `inputs.minecraft.player_position` -> `input.context.player_position`
- `inputs.minecraft.player_look_at` -> `input.context.player_look_direction`
- `inputs.minecraft.selected_block` -> `world.selection.selected_block`
- `inputs.minecraft.selected_region` -> `world.selection.selected_region`
- `inputs.minecraft.biome_at_player` -> `world.read.biome_at_player`
- `inputs.selectors.block_type_selector` -> `input.type_selectors.block_type_selector`
- `inputs.sources.create_list` -> `math.list_sequence.create_list`

Deferred nodes in this wave should not receive v1.0 target aliases yet unless they are also being migrated in code.

## Wave 4: `math.basic`, `math.logic`, `math.randomness`, `math.trigonometry`

Required aliases:

- `math.basic.addition` -> `math.scalar_math.addition`
- `math.basic.subtraction` -> `math.scalar_math.subtraction`
- `math.basic.multiplication` -> `math.scalar_math.multiplication`
- `math.basic.division` -> `math.scalar_math.division`
- `math.basic.modulus` -> `math.scalar_math.modulus`
- `math.basic.absolute` -> `math.scalar_math.absolute`
- `math.basic.clamp` -> `math.scalar_math.clamp`
- `math.basic.remap` -> `math.scalar_math.remap`
- `math.basic.round` -> `math.scalar_math.round`
- `math.basic.floor` -> `math.scalar_math.floor`
- `math.basic.ceiling` -> `math.scalar_math.ceiling`
- `math.basic.min` -> `math.scalar_math.min`
- `math.basic.max` -> `math.scalar_math.max`
- `math.basic.power` -> `math.scalar_math.power`
- `math.basic.logarithm` -> `math.scalar_math.logarithm`
- `math.basic.math_range` -> `math.list_sequence.range`
- `math.logic.equals` -> `math.compare.equals`
- `math.logic.not_equals` -> `math.compare.not_equals`
- `math.logic.less_than` -> `math.compare.less_than`
- `math.logic.less_than_or_equal` -> `math.compare.less_than_or_equal`
- `math.logic.greater_than` -> `math.compare.greater_than`
- `math.logic.greater_than_or_equal` -> `math.compare.greater_than_or_equal`
- `math.logic.select_item` -> `math.logic.switch`
- `math.randomness.random_number` -> `math.random.random_number`
- `math.randomness.noise` -> `math.random.noise_sample`
- `math.randomness.random_list_item` -> `math.random.random_list_item`
- `math.trigonometry.sine` -> `math.trigonometry.sin`
- `math.trigonometry.cosine` -> `math.trigonometry.cos`
- `math.trigonometry.tangent` -> `math.trigonometry.tan`
- `math.trigonometry.arc_sin` -> `math.trigonometry.asin`
- `math.trigonometry.arc_cos` -> `math.trigonometry.acos`
- `math.trigonometry.arc_tan` -> `math.trigonometry.atan2`

No alias needed for unchanged canonical ids:

- `math.logic.and`
- `math.logic.or`
- `math.logic.not`
- `math.logic.if`
- `math.logic.xor`
- `math.trigonometry.degrees_to_radians`
- `math.trigonometry.radians_to_degrees`

## Wave 5: `math.vector`

Required aliases:

- `math.vector.construct_vector` -> `reference.vectors.vector`
- `math.vector.cross_product` -> `reference.vectors.cross_product`
- `math.vector.dot_product` -> `reference.vectors.dot_product`
- `math.vector.normalize_vector` -> `reference.vectors.normalize_vector`
- `math.vector.vector_length` -> `reference.vectors.vector_length`
- `math.vector.vector_addition` -> `reference.vectors.vector_addition`
- `math.vector.vector_subtraction` -> `reference.vectors.vector_subtraction`
- `math.vector.vector_scalar_multiply` -> `reference.vectors.vector_scalar_multiply`
- `math.vector.vector_scalar_divide` -> `reference.vectors.vector_scalar_divide`
- `math.vector.construct_coordinate` -> `reference.points.point_from_coordinates`
- `math.vector.deconstruct_coordinate` -> `reference.points.deconstruct_point`
- `math.vector.midpoint` -> `reference.points.mid_point`
- `math.vector.distance` -> `reference.points.distance_between_points`
- `math.vector.construct_plane` -> `reference.planes.construct_plane`
- `math.vector.construct_plane_from_points` -> `reference.planes.plane_from_points`
- `math.vector.deconstruct_vector` -> `reference.vectors.deconstruct_vector`
- `math.vector.rotate_vector` -> `transform.orientation.rotate_vector`

## Wave 6: `spatial.arrays` and `spatial.points`

Required aliases:

- `spatial.arrays.linear_array` -> `pattern.linear.linear_array`
- `spatial.arrays.grid_array` -> `pattern.grid.grid_array`
- `spatial.arrays.polar_array` -> `pattern.radial.polar_array`
- `spatial.arrays.populate_region` -> `pattern.surface_volume_distribution.populate_region`
- `spatial.points.block_to_point` -> `reference.points.point_from_block`
- `spatial.points.point_between_two_points` -> `reference.points.mid_point`
- `spatial.points.point_along_vector` -> `reference.points.point_along_vector`
- `spatial.points.offset_coordinate` -> `transform.basic_transforms.move_point`
- `spatial.points.offset_coordinates` -> `transform.basic_transforms.move_points`
- `spatial.points.rotate_coordinates` -> `transform.basic_transforms.rotate_points`
- `spatial.points.scale_coordinates` -> `transform.basic_transforms.scale_points`
- `spatial.points.mirror_coordinates` -> `transform.basic_transforms.mirror_points`
- `spatial.points.project_point_to_plane` -> `transform.orientation.project_to_plane`
- `spatial.points.points_to_path` -> `geometry.curves.points_to_path`
- `spatial.points.path_to_points` -> `geometry.curves.path_to_points`
- `spatial.points.distance_point_to_plane` -> `reference.planes.distance_point_to_plane`
- `spatial.points.snap_point_to_block` -> `world.selection.snap_point_to_block`
- `spatial.points.snap_point_list_to_blocks` -> `world.selection.snap_points_to_blocks`
- `spatial.points.point_to_block_if_grid` -> `world.selection.point_to_block_if_grid`
- `spatial.points.is_grid_point` -> `world.query.is_grid_point`
- `spatial.points.filter_grid_points` -> `world.query.filter_grid_points`

Keep existing malformed-id normalization aliases:

- `spatial.points.offsetcoordinates` -> `spatial.points.offset_coordinates`
- `spatial.points.rotatecoordinates` -> `spatial.points.rotate_coordinates`
- `spatial.points.scalecoordinates` -> `spatial.points.scale_coordinates`
- `spatial.points.mirrorcoordinates` -> `spatial.points.mirror_coordinates`
- `spatial.points.randomizecoordinates` -> `spatial.points.randomize_coordinates`

When the v1.0 migration lands, those malformed forms should ideally resolve directly to the final canonical ids instead of stopping at old underscore ids.

## Wave 7: `spatial.construct` and `spatial.modeling`

Required aliases:

- `spatial.construct.box_center_size` -> `geometry.primitives.box`
- `spatial.construct.box_corner_size` -> `geometry.primitives.box_from_corner_size`
- `spatial.construct.box_corners` -> `geometry.primitives.box_from_corners`
- `spatial.construct.sphere_by_center_radius` -> `geometry.primitives.sphere`
- `spatial.construct.sphere_by_diameter` -> `geometry.primitives.sphere_from_diameter`
- `spatial.construct.cylinder_by_axis_radius` -> `geometry.primitives.cylinder`
- `spatial.construct.cone_by_base_apex_radius` -> `geometry.primitives.cone`
- `spatial.construct.ellipsoid_by_center_radii` -> `geometry.primitives.ellipsoid`
- `spatial.construct.octahedron_by_center_size` -> `geometry.primitives.octahedron`
- `spatial.construct.tetrahedron_by_center_edge` -> `geometry.primitives.tetrahedron`
- `spatial.construct.rectangle_on_plane` -> `geometry.profiles.rectangle_profile`
- `spatial.construct.regular_polygon_on_plane` -> `geometry.profiles.polygon_profile`
- `spatial.construct.polygon_by_points` -> `geometry.profiles.custom_profile`
- `spatial.construct.prism_by_profile_vector` -> `geometry.solids.extrude_profile`
- `spatial.construct.prism_by_base_points_vector` -> `geometry.solids.extrude_profile_from_points`
- `spatial.modeling.extrude_profile` -> `geometry.solids.extrude`
- `spatial.modeling.extrude_point_list` -> `geometry.solids.extrude_from_points`
- `spatial.modeling.extrude_box_face` -> `geometry.solids.extrude_box_face`
- `spatial.modeling.loft_profiles` -> `geometry.solids.loft`
- `spatial.modeling.loft_point_lists` -> `geometry.solids.loft_from_points`
- `spatial.modeling.sweep_profile_along_path` -> `geometry.solids.sweep`
- `spatial.modeling.sweep_point_list_along_path` -> `geometry.solids.sweep_from_points`
- `spatial.modeling.resample_polygon_profile` -> `geometry.profiles.resample_profile`
- `spatial.modeling.surface_strip_to_geometry` -> `geometry.solids.surface_strip_to_geometry`
- `spatial.modeling.push_pull_box_face` -> `geometry.solids.push_pull_face`
- `spatial.modeling.twist_point_list` -> `transform.deformations.twist`

## Wave 8: `spatial.analysis`

This wave is large and should be split into smaller PRs if needed.

Required aliases include:

- `spatial.analysis.bounding_box` -> `geometry.boolean.bounding_box`
- `spatial.analysis.geometry_bounds` -> `geometry.boolean.geometry_bounds`
- `spatial.analysis.box_face_to_plane` -> `reference.planes.block_face_plane`
- `spatial.analysis.face_center_frame` -> `reference.frames.frame_from_face`
- `spatial.analysis.face_edge_to_path` -> `geometry.curves.edge_to_curve`
- `spatial.analysis.box_face_boundary_path` -> `geometry.curves.face_boundary_curve`
- `spatial.analysis.deconstruct_box_geometry` -> `geometry.primitives.deconstruct_box`
- `spatial.analysis.deconstruct_sphere` -> `geometry.primitives.deconstruct_sphere`
- `spatial.analysis.deconstruct_cylinder` -> `geometry.primitives.deconstruct_cylinder`
- `spatial.analysis.deconstruct_cone` -> `geometry.primitives.deconstruct_cone`
- `spatial.analysis.deconstruct_ellipsoid` -> `geometry.primitives.deconstruct_ellipsoid`
- `spatial.analysis.deconstruct_octahedron` -> `geometry.primitives.deconstruct_octahedron`
- `spatial.analysis.deconstruct_tetrahedron` -> `geometry.primitives.deconstruct_tetrahedron`
- `spatial.analysis.deconstruct_prism` -> `geometry.primitives.deconstruct_prism`
- `spatial.analysis.deconstruct_polygon_profile` -> `geometry.profiles.deconstruct_profile`
- `spatial.analysis.deconstruct_box_face` -> `reference.points.deconstruct_face`
- `spatial.analysis.deconstruct_face_edge` -> `reference.points.deconstruct_edge`
- `spatial.analysis.closest_point` -> `reference.points.closest_point`
- `spatial.analysis.point_list_center` -> `reference.points.point_list_center`
- `spatial.analysis.point_list_bounds` -> `reference.points.point_list_bounds`
- `spatial.analysis.get_points_in_region` -> `world.read.get_points_in_region`
- `spatial.analysis.is_point_in_region` -> `world.query.is_point_in_region`
- `spatial.analysis.get_box_corner` -> `reference.points.get_box_corner`
- `spatial.analysis.get_box_face` -> `reference.points.get_box_face`
- `spatial.analysis.get_face_edge` -> `reference.points.get_face_edge`
- `spatial.analysis.offset_box_face` -> `transform.basic_transforms.offset_face`
- `spatial.analysis.inset_box_face` -> `transform.basic_transforms.inset_face`
- `spatial.analysis.sample_sphere_surface` -> `pattern.surface_volume_distribution.sample_surface`
- `spatial.analysis.scatter_on_sphere_surface` -> `pattern.surface_volume_distribution.surface_scatter`
- `spatial.analysis.sphere_surface_frame` -> `reference.frames.frame_along_surface`

Deferred analysis nodes should not receive v1.0 canonical aliases until they are actually redesigned:

- `spatial.analysis.geometry_info`
- `spatial.analysis.select_sphere_band_sector`
- `spatial.analysis.sphere_uv`
- `spatial.analysis.sphere_point_info`
- `spatial.analysis.deconstruct_surface_strip`

## Wave 9: `spatial.voxel`

Required aliases:

- `spatial.voxel.geometry_to_blocks` -> `output.execute.bake_geometry_to_blocks`
- `spatial.voxel.surface_strip_to_blocks` -> `output.execute.bake_surface_strip_to_blocks`
- `spatial.voxel.box_geometry_voxelizer` -> `output.execute.bake_box_to_blocks`
- `spatial.voxel.sphere_geometry_voxelizer` -> `output.execute.bake_sphere_to_blocks`
- `spatial.voxel.cylinder_geometry_voxelizer` -> `output.execute.bake_cylinder_to_blocks`
- `spatial.voxel.cone_geometry_voxelizer` -> `output.execute.bake_cone_to_blocks`
- `spatial.voxel.ellipsoid_geometry_voxelizer` -> `output.execute.bake_ellipsoid_to_blocks`
- `spatial.voxel.octahedron_geometry_voxelizer` -> `output.execute.bake_octahedron_to_blocks`
- `spatial.voxel.tetrahedron_geometry_voxelizer` -> `output.execute.bake_tetrahedron_to_blocks`
- `spatial.voxel.torus_geometry_voxelizer` -> `output.execute.bake_torus_to_blocks`
- `spatial.voxel.prism_geometry_voxelizer` -> `output.execute.bake_prism_to_blocks`

These should remain legacy-only for now:

- `spatial.voxel.union_coords`
- `spatial.voxel.intersection_coords`
- `spatial.voxel.difference_coords`

Do not remap those into v1.0 canonical domains until there is a clear placement-boolean design.

## Wave 10: existing `spatial.generators` compatibility debt

Existing support that should stay:

- compact malformed aliases such as `torusblocks -> torus_blocks`
- old moved-node aliases from `spatial.generators.*` to later canonical forms

But once a final v1.0 canonical id exists, aliases should resolve to the final target directly.

Example:

Instead of:

```text
spatial.generators.sphere_by_center_radius -> spatial.construct.sphere_by_center_radius
```

Prefer:

```text
spatial.generators.sphere_by_center_radius -> geometry.primitives.sphere
```

That avoids chain debt.

## 7. Recommended `NodeRegistry` Refactor Shape

To keep the alias map maintainable, split alias construction into helper methods by migration wave.

Recommended structure:

- `addLegacyNormalizationAliases(...)`
- `addLegacyGeneratorAliases(...)`
- `addOutputWaveAliases(...)`
- `addWorldWaveAliases(...)`
- `addInputWaveAliases(...)`
- `addMathWaveAliases(...)`
- `addReferenceWaveAliases(...)`
- `addGeometryWaveAliases(...)`
- `addAnalysisWaveAliases(...)`
- `addExecutionBridgeWaveAliases(...)`

This is not required for correctness, but it will materially improve maintainability.

## 8. Category Overrides

`NODE_CATEGORY_OVERRIDES` should remain minimal.

Preferred rule:

- move node ids to their final canonical domains
- use aliases for old ids
- avoid category override hacks

Use category overrides only when:

- a legacy node must remain registered under an old id temporarily
- but the node library must present it under a compatibility-only category

That should be exceptional, not normal.

## 9. Validation Checklist

For each migration wave:

1. Add aliases for every moved canonical id in that wave.
2. Confirm `resolveCanonicalNodeId(...)` returns the final target id.
3. Confirm old graph files still load.
4. Confirm saving writes only the new canonical id.
5. Confirm duplicate-node and clipboard flows also write only the new canonical id.
6. Confirm node library search finds the migrated node by its new canonical taxonomy.

## 10. After the Alias Plan

After this plan, the implementation order should be:

1. migrate Wave 1 code and aliases
2. compile and smoke test loading/saving
3. migrate Wave 2
4. continue wave by wave

Do not attempt the entire migration in one PR.
