# NodeCraft v1 Current State

Last updated: 2026-04-12

## 1. Scope Baseline

This document is the operational status snapshot for the current node-system refactor.

The product baseline remains:

- architectural form-making
- geometric modeling
- world-grounded construction

The canonical v1 taxonomy remains limited to:

- `input`
- `reference`
- `geometry`
- `transform`
- `pattern`
- `material`
- `world`
- `output`
- `math`

Out of the current v1 mainline:

- `animation.*`
- `flora.*`
- `world.nbt.*`
- `world.inventory.*`
- experimental or out-of-scope utility systems

## 2. Canonical Mainline Status

The following domains are now active canonical paths in code:

- `input.basic`
- `input.numeric`
- `input.context`
- `input.type_selectors`
- `reference.points`
- `reference.vectors`
- `reference.planes`
- `reference.frames`
- `geometry.primitives`
- `geometry.curves`
- `geometry.profiles`
- `geometry.solids`
- `geometry.boolean`
- `transform.basic_transforms`
- `transform.orientation`
- `transform.deformations`
- `pattern.linear`
- `pattern.grid`
- `pattern.radial`
- `pattern.surface_volume_distribution`
- `material.basic_assignment`
- `material.gradient_mapping`
- `world.read`
- `world.query`
- `world.selection`
- `world.write`
- `output.preview`
- `output.execute`
- `output.export`
- `output.debug`
- `math.scalar_math`
- `math.compare`
- `math.logic`
- `math.random`
- `math.trigonometry`
- `math.list_sequence`

## 3. Deferred and Legacy Boundaries

Two non-mainline buckets remain valid:

### 3.1 `deferred.*`

Use `deferred.*` for nodes that are intentionally postponed and should not be treated as v1 canonical modeling nodes.

Current active deferred buckets:

- `deferred.math`
- `deferred.out_of_scope`

Examples:

- `deferred.math.math_series`
- file I/O helpers
- text processing helpers
- advanced selectors and scripting helpers
- workflow / control helpers that are not part of the v1 modeling core

### 3.2 `spatial.legacy`

Use `spatial.legacy` only for backward-compatibility display and loading.

This bucket is not a new feature taxonomy.

It currently holds:

- legacy direct block-output generator ids
- deferred spatial analysis helpers
- deferred instancing helpers
- deferred voxel boolean helpers
- a small number of deferred old Minecraft input helpers

Source of truth:

- [nodecraft-v1-legacy-inventory.md](/f:/development/NC/nodecraft/docs/nodecraft-v1-legacy-inventory.md)

## 4. Utilities Status

`utilities` is no longer a general parking lot.

The temporary mixed utility domains were already split out as follows:

- migrated into canonical mainline where semantics were clear
- moved into `deferred.out_of_scope` where the system is outside v1 scope

The only `utilities` domains that should still remain as actual source organization are:

- `utilities.assist`
- `utilities.organization`
- `utilities.legacy`

This is intentional.

Any new node work should not treat `utilities` as a fallback destination.

## 5. Compatibility Strategy

Compatibility remains supported through:

- explicit node id aliases in [NodeRegistry.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java)
- selective category overrides for `spatial.legacy`
- a small number of editor/runtime fallbacks for older graph content

Compatibility should preserve loading and migration.

Compatibility should not define where new nodes go.

## 6. Practical Rule Set

For any future node addition or migration:

1. If the node belongs to the committed v1 modeling tree, place it directly in its canonical v1 category.
2. If the node is intentionally postponed, place it under `deferred.*`.
3. If the node exists only to keep old graphs loadable, route it through legacy compatibility handling rather than treating it as canonical taxonomy.
4. Do not create new mixed holding areas comparable to the old `utilities` bucket.

## 7. Verification Status

Current verification status as of 2026-04-12:

- `.\gradlew.bat compileJava --no-daemon` passes
- canonical category migration and recent compatibility-layer cleanup compile successfully

What still needs separate validation later:

- old graph load / save compatibility
- clipboard migration edge cases
- runtime node-library behavior across old aliases

## 8. Next-Phase Discipline

The current refactor should no longer be driven by "what old folders still exist".

The correct driver is:

- committed v1 taxonomy
- explicit deferred scope
- explicit legacy compatibility scope

Any future expansion beyond this baseline should be treated as a separate product-scope decision, not as unfinished v1 migration.
