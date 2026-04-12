# NodeCraft v1 Deferred / Legacy Migration Matrix

Last updated: 2026-04-12

## 1. Goal

This matrix answers a narrower question than the cleanup guide:

Which nodes currently under `deferred.*` or `utilities.legacy.*` should be migrated into the main v1 framework, and which should not?

The answer is intentionally conservative.

The goal is not to force every leftover node into the main tree.

The goal is to migrate only the nodes that strengthen the committed v1 modeling framework.

## 2. Confirmed Framework Adjustment

`input.basic` should now be treated as a valid canonical input subcategory.

Reason:

- `TextInputNode`
- `ColorPickerNode`

are useful editor-facing primitive inputs and should remain part of the input system rather than being pushed into `deferred`.

Operational rule:

- `input.basic` is for primitive non-world UI inputs such as text and color
- `input.numeric` remains for scalar/angle/toggle style numeric controls

## 3. Migrate Now

These nodes have a clear home in the current framework and can be migrated without inventing a new sub-system.

### 3.1 `input.basic.text_input`

Current meaning:

- text entry UI node

Recommended target:

- stay in `input.basic`

Action:

- keep
- treat as canonical
- document `input.basic` as a real supported branch

### 3.2 `input.basic.color_picker`

Current meaning:

- color input UI node

Recommended target:

- stay in `input.basic`

Action:

- keep
- treat as canonical
- do not relegate to `deferred`

### 3.3 `utilities.legacy.spatial.analysis.deconstruct_surface_strip`

Current meaning:

- deconstructs `SurfaceStripData` into section paths, flattened points, and rail segments

Recommended target:

- `geometry.solids.deconstruct_surface_strip`

Why it fits:

- `SurfaceStripData` is already a real mainline data type
- `extrude`, `loft`, `sweep`, `prism`, `preview_surface_strip`, and `bake_surface_strip_to_blocks` already operate on it
- this node exposes a committed solid-generation intermediate rather than introducing a parallel subsystem

Action:

- promote to canonical `geometry.solids`
- keep old `spatial.analysis.deconstruct_surface_strip` as an alias only

## 4. Migrate Only After Product Decision

These nodes can plausibly fit the framework, but only if you explicitly want that capability in v1 or v1.x.

### 4.1 `utilities.legacy.spatial.points.point_between_two_points`

Current meaning:

- point interpolation between A and B with a parameter `t`

Why it should not be collapsed into midpoint:

- it is not a pure midpoint node
- it exposes interpolation behavior that `reference.points.mid_point` does not
- aliasing it to midpoint would silently change old graph behavior

Recommendation:

- keep it in legacy for now
- only promote it after a real canonical node exists for point interpolation, such as a future line/curve parameter node

### 4.2 `utilities.legacy.spatial.points.randomize_coordinates`

Current meaning:

- random jitter applied to coordinate lists

Possible homes:

- `transform.deformations`
- `pattern.surface_volume_distribution`

Recommendation:

- migrate only if you want procedural jitter/displacement as part of the main modeling language

Best fit if promoted:

- `transform.deformations.noise_displace` or `transform.deformations.random_jitter`

### 4.3 `utilities.legacy.spatial.instancing.grow_along_normals`
- `utilities.legacy.spatial.instancing.grow_along_sphere_normal`

Current meaning:

- grow geometry along point-normal directions

Possible homes:

- `pattern.surface_volume_distribution`
- `geometry.solids`

Recommendation:

- promote only if surface-driven growth / placement becomes a first-class modeling workflow

These should not be migrated just because they are geometrically related.

### 4.4 `utilities.legacy.spatial.analysis.deconstruct_surface_strip`

Current meaning:

- decomposes surface-strip data into paths, points, and rails

Possible homes:

- `geometry.curves`
- `geometry.solids`

Recommendation:

- migrate only if `SurfaceStripData` is staying as a real modeling primitive

If surface strips are not a committed public modeling concept, keep this out of the main tree.

### 4.5 `deferred.out_of_scope.color_to_components`
- `deferred.out_of_scope.components_to_color`

Current meaning:

- color data decomposition / reconstruction

Possible homes:

- `input.basic`
- future dedicated color/material utility layer

Recommendation:

- only promote if color becomes a real first-class authoring primitive in the node language

Right now `ColorPickerNode` alone is useful.
Color conversion utilities are not yet necessary as canonical mainline nodes.

### 4.6 `deferred.out_of_scope.number_to_boolean`

Current meaning:

- numeric cast to boolean

Possible homes:

- `math.logic`

Recommendation:

- migrate only if you explicitly want type-cast nodes in the main math system

At the moment this is not stronger than using compare logic directly.

## 5. Keep Deferred

These should remain deferred for now.

### 5.1 Text-processing family

- `concatenate_text`
- `find_replace_text`
- `format_text`
- `join_text`
- `split_text`
- `text_length`
- `text_to_value`
- `text_panel`

Reason:

- useful in a generic editor language
- not part of the committed building/modeling/world framework

### 5.2 File I/O family

- `file_path`
- `load_graph`
- `save_graph`
- `read_data_file`
- `read_text_file`
- `write_data_file`
- `write_text_file`

Reason:

- tooling/system functionality
- not a modeling taxonomy concern

### 5.3 Advanced scripting / attribute family

- `eval_expression`
- `script`
- `node_group`
- `get_attribute`
- `set_attribute`
- `filter_by_attribute`

Reason:

- they define a parallel generic workflow language
- not a core v1 modeling requirement

### 5.4 External selector family

- `effect_type_selector`
- `entity_type_selector`
- `item_type_selector`
- `sound_event_selector`

Reason:

- outside the current building/modeling/world-surface scope

### 5.5 Workflow control family

- `for_each`
- `geometry_gate`
- `geometry_merge`
- `geometry_passthrough`
- `geometry_switch`

Reason:

- these are workflow/execution abstractions
- they should not be promoted into the geometry or math tree casually

## 6. Keep Legacy Or Delete, But Do Not Promote

These legacy nodes should either stay in `spatial.legacy` or be deleted later.
They should not be migrated into canonical v1.

### 6.1 Direct block-output generator family

- all `*_blocks` generator nodes under `utilities.legacy.spatial.generators`

Reason:

- they bypass the intended main pipeline:
  - reference -> geometry -> transform/pattern -> material -> output/world
- they are exactly the kind of historical shortcut the new architecture is trying to replace

### 6.2 Voxel boolean coord helpers

- `union_coords`
- `intersection_coords`
- `difference_coords`

Reason:

- low-level coord-set operations
- not aligned with the canonical geometry boolean layer
- likely to create confusion if promoted directly

### 6.3 Sphere-specialized analysis helpers

- `geometry_info`
- `select_sphere_band_sector`
- `sphere_uv`
- `sphere_point_info`

Reason:

- too specialized for the current mainline
- not foundational enough to justify canonical placement now

## 7. Recommended Execution Order

Use this order:

1. Formalize `input.basic` as canonical
2. Promote `deconstruct_surface_strip` into `geometry.solids`
3. Keep `point_between_two_points` in legacy until a real canonical interpolation node exists
4. Decide whether `randomize_coordinates` belongs in `transform.deformations`
5. Decide whether surface-growth nodes belong in `pattern.surface_volume_distribution`
6. Leave the rest deferred or legacy

## 8. Strong Recommendation

Do not measure progress by how many deferred or legacy nodes get migrated.

Measure progress by whether the main v1 framework becomes:

- clearer
- smaller
- more composable
- less redundant

If a deferred or legacy node does not clearly improve that framework, do not promote it.
