# NodeCraft v1.0 Old-to-New Category Migration Map

This document defines the migration mapping from the current legacy taxonomy to the NodeCraft v1.0 canonical taxonomy.

It should be used for:

- node-by-node migration planning
- package moves
- `@NodeInfo(category = ...)` updates
- canonical id renames
- alias compatibility design
- node library presentation updates

This document is intentionally stricter than a rough planning note.

The goal is to reduce ambiguity before migration work starts.

## 1. Migration Principles

### 1.1 Category migration is semantic, not mechanical

Old package path or old category does not automatically determine the new category.

Migration must follow semantic responsibility.

### 1.2 One old category may split into multiple new categories

This happens frequently in the current codebase.

Examples:

- `inputs.basic`
- `inputs.minecraft`
- `math.vector`
- `spatial.construct`
- `spatial.analysis`

These must be migrated node by node using the mapping rules below.

### 1.3 Excluded domains do not get forced into the v1.0 main tree

If a legacy category belongs to an excluded domain, mark it as deferred (or removed when physically deleted) instead of force-fitting it into a wrong v1.0 category.

Excluded or deferred domains include:

- animation
- flora
- nbt
- inventory
- entity behavior
- command / interaction workflow
- utilities / advanced workflow
- experimental nodes

### 1.4 Compatibility is handled by aliases, not by taxonomy duplication

If an old node id or category changes, preserve compatibility through:

- id aliases
- import/load remapping
- UI shortcuts

Do not preserve compatibility by keeping duplicate canonical homes.

## 2. Status Meanings

Migration statuses in this document:

- `direct`
  - old category maps cleanly to one new category
- `split`
  - old category must be split across multiple new categories
- `deferred`
  - outside the v1.0 main taxonomy for now
- `legacy`
  - kept only for compatibility during migration, not as a v1.0 target

## 3. Top-Level Migration Summary

| Old Top-Level | v1.0 Outcome | Status | Notes |
|---|---|---|---|
| `inputs` | `input`, `world`, `math` | split | Old inputs mixed true input, world selection, and data construction |
| `math` | `math`, `reference`, `transform` | split | Old math also contains spatial-reference semantics |
| `spatial` | `reference`, `geometry`, `transform`, `pattern`, `output` | split | Main geometry debt area |
| `world` | `world`, `material` | split | Query/read/write stay; some material-style nodes move out |
| `visualization` | `output` | direct | Preview, execute, debug map cleanly |
| `data` | deferred | deferred | Not part of v1.0 main tree |
| `control` | `math`, deferred | split | Small reusable logic subset moves, rest deferred |
| `utilities` | deferred | deferred | Workflow-only system, not core v1.0 tree |
| `animation` | removed | removed | Explicitly out of scope; physically removed on 2026-04-11 |
| `flora` | removed | removed | Explicitly out of scope; physically removed on 2026-04-11 |

## 4. Detailed Old-to-New Mapping

## 4.1 `inputs.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `inputs.basic` | split across `input.numeric`, `reference.points`, `reference.vectors`, `reference.planes` | split | Basic scalar inputs go to `input.numeric`; vector/coordinate/plane constructors move to reference families |
| `inputs.minecraft` | split across `input.context`, `world.selection`, `world.read`, deferred | split | Player context stays in `input.context`; selected block/region move to `world.selection`; non-core world facts may defer |
| `inputs.selectors` | split across `input.type_selectors`, deferred | split | Block-related selectors enter v1.0; item/entity/effect/sound selectors defer |
| `inputs.sources` | split across `math.list_sequence`, deferred | split | `Create List` moves to `math.list_sequence`; file/text workflow nodes defer |

Representative node mapping:

- `inputs.basic.integer_input` -> `input.numeric`
- `inputs.basic.float_input` -> `input.numeric`
- `inputs.basic.integer_slider` -> `input.numeric`
- `inputs.basic.float_slider` -> `input.numeric`
- `inputs.basic.angle_slider` -> `input.numeric`
- `inputs.basic.circular_angle` -> `input.numeric`
- `inputs.basic.boolean_toggle` -> `input.numeric`
- `inputs.basic.vector_input` -> `reference.vectors`
- `inputs.basic.coordinate_input` -> `reference.points`
- `inputs.basic.plane_selector` -> `reference.planes`
- `inputs.basic.text_input` -> deferred
- `inputs.basic.color_picker` -> deferred
- `inputs.minecraft.player_position` -> `input.context`
- `inputs.minecraft.player_look_at` or `player_look_direction` -> `input.context`
- `inputs.minecraft.selected_block` -> `world.selection`
- `inputs.minecraft.selected_region` -> `world.selection`
- `inputs.minecraft.selected_entity` -> deferred
- `inputs.minecraft.selected_block_sequence` -> deferred
- `inputs.minecraft.current_time` -> `input.context.current_time`
- `inputs.minecraft.dimension_info` -> `input.context.dimension_info`
- `inputs.minecraft.biome_at_player` -> `world.read` or deferred depending retained behavior
- `inputs.selectors.block_type_selector` -> `input.type_selectors`
- `inputs.selectors.item_type_selector` -> deferred
- `inputs.selectors.entity_type_selector` -> deferred
- `inputs.selectors.effect_type_selector` -> deferred
- `inputs.selectors.sound_event_selector` -> deferred
- `inputs.sources.create_list` -> `math.list_sequence`
- `inputs.sources.text_panel` -> deferred
- `inputs.sources.file_path` -> deferred

## 4.2 `math.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `math.basic` | `math.scalar_math` | direct | Scalar arithmetic remains math |
| `math.logic` | split across `math.logic`, `math.compare` | split | Boolean logic stays in logic; relational checks go to compare |
| `math.randomness` | `math.random` | direct | Random and noise stay random |
| `math.trigonometry` | `math.trigonometry` | direct | Direct rename |
| `math.vector` | split across `reference.points`, `reference.vectors`, `reference.planes`, `transform.orientation` | split | Legacy vector category mixed spatial references, measurements, and orientation helpers |

Representative node mapping:

- `math.basic.*` -> `math.scalar_math`
- `math.logic.and` / `or` / `not` / `if` -> `math.logic`
- relational comparison nodes -> `math.compare`
- `math.randomness.*` -> `math.random`
- `math.trigonometry.*` -> `math.trigonometry`
- `math.vector.construct_vector` -> `reference.vectors`
- `math.vector.cross_product` -> `reference.vectors`
- `math.vector.dot_product` -> `reference.vectors`
- `math.vector.normalize_vector` -> `reference.vectors`
- `math.vector.midpoint` -> `reference.points`
- `math.vector.distance` -> `math.compare` or `reference.points` helper depending retained node semantics
- `math.vector.construct_plane` -> `reference.planes`
- `math.vector.construct_plane_from_points` -> `reference.planes`
- `math.vector.rotate_vector` -> `transform.orientation` if kept as transform-style utility
- `math.vector.deconstruct_coordinate` -> `reference.points`
- `math.vector.deconstruct_vector` -> `reference.vectors`

## 4.3 `spatial.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `spatial.points` | split across `reference.points`, `geometry.curves`, `transform.basic_transforms`, `transform.orientation` | split | Point primitives, path helpers, and point transforms were historically mixed |
| `spatial.construct` | split across `geometry.primitives`, `geometry.curves`, `geometry.profiles`, `geometry.architectural_primitives` | split | Construction nodes should migrate to semantic geometry families |
| `spatial.analysis` | split across `reference.*`, `geometry.*`, `pattern.*` | split | This is a heavy node-level migration area |
| `spatial.modeling` | split across `geometry.solids`, `transform.deformations` | split | Modeling contains both solid-generation and deformation behavior |
| `spatial.arrays` | split across `pattern.linear`, `pattern.grid`, `pattern.radial`, `pattern.surface_volume_distribution` | split | Array semantics map cleanly into new pattern families |
| `spatial.instancing` | split across `pattern.along_curve`, `pattern.surface_volume_distribution`, deferred | split | Placement/growth semantics need node-level review |
| `spatial.voxel` | split across `output.execute`, deferred | split | Geometry-to-block bridge belongs near execution; boolean coord utilities may defer |
| `spatial.sdf` | deferred | deferred | Explicitly outside v1.0 main tree for now |
| `spatial.generators` | semantic target is `geometry.*` plus `output.execute`; old category itself is legacy-only | legacy | Direct block-output generator debt must not remain canonical |

Representative node mapping:

- `spatial.points.block_to_point` -> `reference.points`
- `spatial.points.point_between_two_points` -> `reference.points`
- `spatial.points.point_along_vector` -> `reference.points`
- `spatial.points.project_point_to_plane` -> `transform.orientation` or `reference.points` depending retained semantics
- `spatial.points.offset_coordinates` -> `transform.basic_transforms`
- `spatial.points.rotate_coordinates` -> `transform.basic_transforms`
- `spatial.points.scale_coordinates` -> `transform.basic_transforms`
- `spatial.points.mirror_coordinates` -> `transform.basic_transforms`
- `spatial.points.points_to_path` -> `geometry.curves`
- `spatial.points.path_to_points` -> `geometry.curves`
- `spatial.construct.box_*` -> `geometry.primitives`
- `spatial.construct.sphere_*` -> `geometry.primitives`
- `spatial.construct.cylinder_*` -> `geometry.primitives`
- `spatial.construct.cone_*` -> `geometry.primitives`
- `spatial.construct.ellipsoid_*` -> `geometry.primitives`
- `spatial.construct.tetrahedron_*` / `octahedron_*` -> `geometry.primitives`
- `spatial.construct.regular_polygon_on_plane` -> `geometry.profiles`
- `spatial.construct.rectangle_on_plane` -> `geometry.profiles`
- `spatial.construct.polygon_by_points` -> `geometry.profiles`
- `spatial.construct.prism_*` -> `geometry.primitives` or `geometry.solids` depending retained node contract
- `spatial.modeling.extrude_*` -> `geometry.solids`
- `spatial.modeling.loft_*` -> `geometry.solids`
- `spatial.modeling.sweep_*` -> `geometry.solids`
- `spatial.modeling.twist_*` -> `transform.deformations`
- `spatial.modeling.push_pull_box_face` -> `geometry.solids` or `transform.basic_transforms` depending final semantic contract
- `spatial.arrays.linear_array` -> `pattern.linear`
- `spatial.arrays.grid_array` -> `pattern.grid`
- `spatial.arrays.polar_array` -> `pattern.radial`
- `spatial.arrays.populate_region` -> `pattern.surface_volume_distribution`
- `spatial.instancing.grow_along_normals` -> deferred unless retained as distribution logic
- `spatial.instancing.grow_along_sphere_normal` -> deferred unless retained as distribution logic
- `spatial.voxel.geometry_to_blocks` -> `output.execute`
- primitive-specific voxelizers -> `output.execute` or removed once unified bake path exists
- `spatial.sdf.*` -> removed (2026-04-11)
- `spatial.generators.*_blocks` -> legacy compatibility only; semantically split into `geometry.*` plus `output.execute`

## 4.4 `world.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `world.query` | split across `world.read`, `world.query` | split | Data retrieval and environmental checks must be separated |
| `world.modification` | split across `world.write`, `material.basic_assignment` | split | Direct write nodes stay in `world.write`; material-style mapping nodes move to material |
| `world.nbt` | removed | removed | Out of v1.0 main scope; physically removed on 2026-04-11 |
| `world.inventory` | removed | removed | Out of v1.0 main scope; physically removed on 2026-04-11 |
| `world.entity` | deferred | deferred | Out of v1.0 main scope |
| `world.interaction` | deferred | deferred | Out of v1.0 main scope |

Representative node mapping:

- `world.query.get_block` -> `world.read`
- `world.query.get_blocks_in_region` -> `world.read`
- `world.query.find_blocks` -> `world.read`
- `world.query.get_biome` -> `world.read`
- `world.query.get_light_level` -> `world.query`
- `world.query.get_fluid_level` -> `world.query`
- `world.query.check_replaceable` -> `world.query`
- `world.query.check_loaded_chunks` -> `world.query`
- `world.query.is_solid_block` -> `world.query`
- `world.modification.set_block` -> `world.write`
- `world.modification.set_blocks` -> `world.write`
- `world.modification.fill_region` -> `world.write`
- `world.modification.replace_blocks` -> `world.write`
- `world.modification.clone_region` -> `world.write`
- `world.modification.remove_blocks` or clear-style nodes -> `world.write`
- `world.modification.material_mapper` -> `material.basic_assignment`

## 4.5 `visualization.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `visualization.preview` | `output.preview` | direct | Pure rename by responsibility |
| `visualization.execute` | `output.execute` | direct | Pure rename by responsibility |
| `visualization.debugging` | `output.debug` | direct | Pure rename by responsibility |

Representative node mapping:

- `visualization.preview.geometry_viewer` -> `output.preview`
- `visualization.preview.preview_blocks` -> `output.preview`
- `visualization.preview.preview_points` -> `output.preview`
- `visualization.preview.preview_curves` / `paths` -> `output.preview`
- `visualization.preview.preview_regions` -> `output.preview`
- `visualization.preview.preview_vectors` / `plane` / `frame` / `labels` -> `output.preview`
- `visualization.execute.apply_changes` -> `output.execute`
- `visualization.execute.export_schematic` -> `output.export`
- `visualization.debugging.value_monitor` -> `output.debug`
- `visualization.debugging.print_to_chat` -> `output.debug`
- `visualization.debugging.execution_timer` -> `output.debug`
- `visualization.debugging.panel` -> deferred unless retained as inspector-style debug node

## 4.6 `control.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `control.flow` | split across `math.compare`, `math.logic`, deferred | split | Reusable pure-logic nodes move; execution-control nodes defer |

Representative node mapping:

- `control.flow.compare` -> `math.compare`
- `control.flow.branch` -> `math.logic`
- `control.flow.switch` -> `math.logic`
- `control.flow.for_each` -> deferred
- `control.flow.geometry_gate` -> deferred
- `control.flow.geometry_switch` -> deferred
- `control.flow.geometry_merge` -> deferred
- `control.flow.geometry_passthrough` -> deferred

## 4.7 `data.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `data.lists` | partially `math.list_sequence`, otherwise deferred | split | Generic list operations needed by v1.0 move to math; general data system remains outside main taxonomy |
| `data.sequence` | partially `math.list_sequence`, otherwise deferred | split | Range/repeat style nodes can migrate; broader sequence tooling may defer |
| `data.conversion` | deferred | deferred | Not part of the v1.0 main taxonomy |
| `data.text` | deferred | deferred | Not part of the v1.0 main taxonomy |

Representative node mapping:

- `data.lists.create_list` if any canonical version remains -> `math.list_sequence`
- `data.lists.list_length` -> `math.list_sequence`
- `data.lists.get_item` -> `math.list_sequence`
- `data.lists.sub_list` / `slice_list` -> `math.list_sequence`
- `data.lists.combine_lists` / `zip_lists` -> `math.list_sequence`
- `data.sequence.range` -> `math.list_sequence`
- `data.sequence.repeat` -> `math.list_sequence`
- text and conversion nodes -> deferred

## 4.8 `utilities.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `utilities.assist` | deferred | deferred | Editor-workflow nodes, not part of v1.0 main modeling taxonomy |
| `utilities.organization` | deferred | deferred | Comment/group/layout nodes stay outside canonical node taxonomy |
| `utilities.fileio` | deferred | deferred | Graph/file workflow, not core build taxonomy |
| `utilities.advanced` | deferred | deferred | Script/attribute/eval layer excluded from v1.0 |
| `utilities.experimental` | deferred | deferred | Explicitly excluded |

## 4.9 `animation.*` and `flora.*`

| Old Category | New Category | Status | Rule |
|---|---|---|---|
| `animation.*` | removed | removed | Entire domain excluded from v1.0 main tree; physically removed on 2026-04-11 |
| `flora.*` | removed | removed | Entire domain excluded from v1.0 main tree; physically removed on 2026-04-11 |

## 5. Migration Work Order

Recommended order:

1. Migrate `visualization.*` to `output.*`
2. Migrate `world.query` and `world.modification`
3. Migrate `inputs.*`
4. Migrate `math.*`
5. Migrate `spatial.construct`, `spatial.modeling`, `spatial.arrays`
6. Migrate `spatial.points` and `spatial.analysis`
7. Resolve `spatial.voxel` and `spatial.generators` legacy bridge
8. Leave deferred domains untouched except for compatibility handling

Reason:

- start with the clean direct renames
- then stabilize world and input boundaries
- then resolve geometry and reference semantics on top of a clearer taxonomy

## 6. Risk Areas Requiring Node-Level Review

The following old categories are not safe for bulk category replacement:

- `inputs.basic`
- `inputs.minecraft`
- `math.vector`
- `spatial.points`
- `spatial.construct`
- `spatial.analysis`
- `spatial.modeling`
- `spatial.instancing`
- `spatial.voxel`
- `spatial.generators`
- `control.flow`

These require node-by-node migration tables or implementation review before editing canonical ids.

## 7. Hard Rules During Migration

While migration is ongoing:

- do not create new canonical ids under `spatial.*`
- do not create new canonical ids under `visualization.*`
- do not create new canonical ids under `inputs.*`
- do not force deferred nodes into the v1.0 main tree
- do not preserve old taxonomy by duplicating categories in the node library
- do preserve backward compatibility through alias mappings

## 8. Next Migration Artifact

This document is the category-level migration map.

The next required artifact is:

- a node-by-node canonical migration table

That table should include at minimum:

- old node id
- old category
- new node id
- new category
- alias required or not
- package move required or not
- deferred or in-scope
