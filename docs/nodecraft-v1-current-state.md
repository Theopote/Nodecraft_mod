# NodeCraft v1 Current State

Last updated: 2026-04-12

## Scope Baseline

The active refactor is still limited to:

- architectural form-making
- geometric modeling
- world-grounded construction

The canonical top-level taxonomy is:

- `input`
- `reference`
- `geometry`
- `transform`
- `pattern`
- `material`
- `world`
- `output`
- `math`

Out of scope for the current mainline:

- `animation.*`
- `flora.*`
- `world.nbt.*`
- `world.inventory.*`
- large workflow / scripting / text / file utility systems

## Active Canonical Paths

The codebase currently uses these canonical v1 paths:

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
- `material.directional_mapping`
- `material.pattern_mapping`
- `material.block_state`
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

## Removed Compatibility Buckets

The following source trees have been physically removed:

- `nodes/deferred`
- `nodes/utilities/legacy`

That removal is intentional.

The node system is no longer organized around cold-storage or legacy buckets. If a node does not belong to the committed v1 framework, it should not stay in the active source tree.

## Utilities Boundary

`utilities` is no longer a parking lot.

Only these editor-side utility areas should remain:

- `utilities.assist`
- `utilities.organization`

They are not part of the modeling taxonomy.

## Alias Policy

Compatibility is now limited to a small set of direct old-to-new renames handled in `NodeRegistry`.

The registry no longer acts as a broad bridge for:

- `deferred.*`
- removed legacy utility trees
- large deleted subsystems

## Practical Rule Set

For future work:

1. If a node belongs to the v1 framework, add it directly to its canonical category.
2. If it does not belong to the framework, do not create a new holding bucket.
3. If you still need a rename alias, keep it narrow and explicit.
4. Do not reintroduce migration-only domains as source structure.

## Verification Status

Current verification status:

- `.\gradlew.bat compileJava --no-daemon` passes
- `.\gradlew.bat testClasses --no-daemon` passes

## Current Source of Truth

- taxonomy and alias behavior:
  - [NodeRegistry.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java)
- library ordering and visible categories:
  - [NodeLibraryComponent.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/gui/components/NodeLibraryComponent.java)
- built-in category registration:
  - [DefaultNodeProvider.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/core/DefaultNodeProvider.java)
