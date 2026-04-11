# NodeCraft v1.0 Migration Audit

This document records the current migration status of the node system against the v1.0 taxonomy and migration ledger.

It is intended to answer one question:

Which parts of the codebase are already aligned with the v1.0 canonical tree, and which parts still need migration or explicit defer/legacy handling?

## 1. Audit Scope

This audit focuses on the v1.0 main-tree refactor defined in:

- `docs/NodeCraft-v1.0-节点分类树-定稿.md`
- `docs/nodecraft-v1-category-id-guidelines.md`
- `docs/nodecraft-v1-category-migration-map.md`
- `docs/nodecraft-v1-node-migration-ledger.md`
- `docs/nodecraft-v1-node-alias-plan.md`
- `docs/nodecraft-v1-legacy-inventory.md`

Out-of-scope domains are not treated as migration failures:

- `animation.*`
- `flora.*`
- `world.entity.*`
- `world.inventory.*`
- `world.nbt.*`
- `utilities.*`
- deferred text / file / entity / experimental domains

Status note (2026-04-11):

- `animation.*`, `flora.*`, `world.inventory.*`, and `world.nbt.*` were physically removed from source.

## 2. Status Summary

### 2.1 Main conclusion

The refactor is structurally far along.

The following v1.0 domains are already largely migrated to canonical package/category/id form:

- `input.*`
- `reference.*`
- `geometry.*`
- `transform.*`
- `pattern.*`
- `material.basic_assignment`
- `world.read`
- `world.write`
- `world.selection`
- `world.query` for the committed v1 query subset
- `output.*`

The main remaining canonical gap is the math taxonomy.

Several math nodes still live under pre-v1 category names even though their target canonical homes are already defined in the migration ledger.

### 2.2 Current high-level buckets

From the current source scan of `src/main/java/com/nodecraft/nodesystem/nodes`:

- `canonical_v1 prefix present`: many nodes are already under `input / reference / geometry / transform / pattern / material / world / output / math`
- `old_or_legacy`: old `spatial.* / inputs.* / data.* / control.*` nodes still exist
- `other`: out-of-scope domains such as `utilities.*` and excluded world domains (with `animation.*` / `flora.*` now physically removed)

Important caveat:

Being under the top-level `math.*` namespace does not automatically mean the node is fully migrated.

For example:

- `math.basic.*`
- `math.randomness.*`
- `math.vector.*`

still need canonical cleanup according to the ledger.

## 3. Areas Already Aligned

These areas are effectively on the v1 main path already.

### 3.1 Output

Completed:

- `output.preview.*`
- `output.execute.*`
- `output.export.*`
- `output.debug.*`

Old `visualization.*` paths are now compatibility aliases rather than canonical homes.

### 3.2 Input

Completed:

- `input.numeric.*`
- `input.context.*`
- `input.type_selectors.block_type_selector`

Also completed:

- `inputs.minecraft.selected_block -> world.selection.selected_block`
- `inputs.minecraft.selected_region -> world.selection.selected_region`
- `inputs.sources.create_list -> math.list_sequence.create_list`

### 3.3 Reference

Completed:

- `reference.points.*`
- `reference.vectors.*`
- `reference.planes.*`
- `reference.frames.*`

This includes the former `inputs.basic`, `math.vector`, `spatial.points`, and `spatial.analysis` reference helpers that were explicitly migrated.

### 3.4 Geometry

Completed:

- `geometry.primitives.*`
- `geometry.curves.*`
- `geometry.profiles.*`
- `geometry.solids.*`
- `geometry.boolean.*`

This includes the main former `spatial.construct` and `spatial.modeling` migration batches.

### 3.5 Transform

Completed:

- `transform.basic_transforms.*`
- `transform.orientation.*`
- `transform.deformations.twist`

### 3.6 Pattern

Completed:

- `pattern.linear.*`
- `pattern.grid.*`
- `pattern.radial.*`
- `pattern.surface_volume_distribution.*`

This covers the committed `spatial.arrays` migration wave.

### 3.7 World

Completed:

- `world.read.*` main committed subset
- `world.write.*`
- `world.selection.*`
- `world.query.*` committed subset:
  - `get_light_level`
  - `get_fluid_level`
  - `is_grid_point`
  - `filter_grid_points`
  - `is_point_in_region`

### 3.8 Material

Completed:

- `material.basic_assignment.replace_material`

## 4. Remaining Canonical Migration Work

These nodes are still not at their final canonical v1.0 homes according to the migration ledger.

This is the real main-path remainder.

### 4.1 Math taxonomy is still partially pre-v1

#### 4.1.1 `math.basic.*`

Still old:

- `math.basic.absolute`
- `math.basic.addition`
- `math.basic.ceiling`
- `math.basic.clamp`
- `math.basic.division`
- `math.basic.floor`
- `math.basic.logarithm`
- `math.basic.max`
- `math.basic.min`
- `math.basic.modulus`
- `math.basic.multiplication`
- `math.basic.power`
- `math.basic.remap`
- `math.basic.round`
- `math.basic.subtraction`

Ledger target:

- move to `math.scalar_math.*`

Special cases:

- `math.basic.range` should move to `math.list_sequence.range`
- `math.basic.series` is not committed to the v1 main tree and should be treated as deferred unless semantics are redesigned

#### 4.1.2 `math.logic.*`

Still mixed:

Already acceptable in principle:

- `math.logic.and`
- `math.logic.or`
- `math.logic.not`
- `math.logic.if`
- `math.logic.xor`

Still pending by ledger:

- `math.logic.equals -> math.compare.equals`
- `math.logic.not_equals -> math.compare.not_equals`
- `math.logic.less_than -> math.compare.less_than`
- `math.logic.less_than_or_equal -> math.compare.less_than_or_equal`
- `math.logic.greater_than -> math.compare.greater_than`
- `math.logic.greater_than_or_equal -> math.compare.greater_than_or_equal`
- `math.logic.select_item -> math.logic.switch`

#### 4.1.3 `math.randomness.*`

Still old:

- `math.randomness.random_number`
- `math.randomness.noise`
- `math.randomness.random_list_item`
- `math.randomness.random_vector`

Ledger target:

- `random_number -> math.random.random_number`
- `noise -> math.random.noise_sample`
- `random_list_item -> math.random.random_list_item`
- `random_vector` should remain deferred

#### 4.1.4 `math.trigonometry.*`

Still using old naming:

- `sine`
- `cosine`
- `tangent`
- `arcsin`
- `arccos`
- `arctan`
- `pi`

Ledger target:

- `sin`
- `cos`
- `tan`
- `asin`
- `acos`
- `atan2` or `atan` after confirming implementation semantics

Special case:

- `pi` is currently outside the committed v1 essential set and should be treated as deferred unless promoted explicitly

#### 4.1.5 `math.vector.*`

Still old:

- `math.vector.construct`
- `math.vector.construct_coordinate`

Ledger target:

- `math.vector.construct -> reference.vectors.vector`
- `math.vector.construct_coordinate -> reference.points.point_from_coordinates`

This is a genuine remaining migration gap because the canonical replacements already exist.

### 4.2 Summary of recommended next migration wave

The next real refactor wave should be:

1. `math.basic -> math.scalar_math / math.list_sequence / deferred`
2. `math.logic compare-family -> math.compare`
3. `math.randomness -> math.random`
4. `math.trigonometry legacy names -> canonical short names`
5. remove the remaining live `math.vector` constructor nodes in favor of `reference.*`

## 5. Deferred and Legacy Inventory Check

These items are still outside the v1 main tree, but that is expected and consistent with the documents.

### 5.1 Expected deferred input nodes

These are still old on purpose:

- `inputs.basic.text_input`
- `inputs.basic.color_picker`
- `inputs.selectors.item_type_selector`
- `inputs.selectors.entity_type_selector`
- `inputs.selectors.effect_type_selector`
- `inputs.selectors.sound_event_selector`
- `inputs.sources.text_panel`
- `inputs.sources.file_path`

### 5.2 Expected legacy / deferred world-input nodes

- `inputs.minecraft.selected_entity`
- `inputs.minecraft.selected_block_sequence`

These should remain compatibility-only and continue to display under `spatial.legacy`.

### 5.3 Expected legacy spatial nodes

These are already intentionally outside the main tree:

- `spatial.analysis.*` surviving deferred nodes
- `spatial.instancing.*`
- `spatial.points.point_between_two_points`
- `spatial.points.randomize_coordinates`
- `spatial.voxel.union_coords`
- `spatial.voxel.intersection_coords`
- `spatial.voxel.difference_coords`
- `spatial.generators.*_blocks`

These should not be treated as incomplete migration work unless the v1 scope changes.

## 6. Open Classification Drift

These items are not currently blocking the v1 main-tree migration, but they need an explicit disposition.

### 6.1 `spatial.sdf.*`

Current status:

- `spatial.sdf.*` was physically removed from source on 2026-04-11.

Problem:

These nodes are not covered clearly by the current v1 migration ledger.

Recommendation:

Do not silently treat them as canonical.

Choose one of:

- mark as deferred
- mark as legacy
- or create a separate SDF appendix if the design is still intended

Historic note: before removal, this domain was considered out of the v1 main tree pending explicit product decision.

### 6.2 Out-of-scope domains (current status)

These domains do not currently block the v1 main-tree refactor:

- `control.flow.*`
- `data.*`
- `utilities.*`
- `world.entity.*`
- `world.interaction.*`

Physically removed on 2026-04-11:

- `animation.*`
- `flora.*`
- `world.inventory.*`
- `world.nbt.*`
- `spatial.sdf.*`

They should not be mixed into the canonical v1 taxonomy unless the committed scope expands.

## 7. Audit Verdict

The main migration is not blocked by `spatial.*` anymore.

The actual unfinished main-line work is now mostly:

- math taxonomy normalization
- continued restraint so deferred / legacy domains are not mistaken for canonical v1 coverage

In practical terms:

- geometry/reference/transform/pattern/output/world core migration is already in good shape
- math is the next proper refactor target
- after math, the next step should be to shrink old package exposure rather than continuing broad cosmetic cleanup
