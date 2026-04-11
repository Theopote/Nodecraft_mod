# NodeCraft Framework Alignment Audit

Last updated: 2026-04-11

## Target Framework

All nodes should ultimately live under these top-level families:

- Input
- Reference
- Geometry
- Transform
- Pattern
- Material
- World
- Output
- Math & Logic
- Utilities

## Current Status (From `@NodeInfo(category=...)` Scan)

Top-level counts already inside the target framework:

- `geometry`: 40
- `input`: 12
- `material`: 1
- `math`: 42
- `output`: 30
- `pattern`: 6
- `reference`: 31
- `transform`: 10
- `utilities`: 19
- `world`: 32

Outside-framework categories still present: 79 nodes total.

## Outside-Framework Inventory and Target Placement

### 1) `data.*` (30 nodes)

Current:

- `data.lists` (14)
- `data.sequence` (3)
- `data.conversion` (7)
- `data.text` (6)

Recommended target:

- `data.lists.*` -> `math.list_sequence.*`
- `data.sequence.range/repeat` -> `math.list_sequence.*`
- `data.sequence.series` -> `deferred.math.math_series` or `math.list_sequence.series` (product decision)
- `data.conversion.*` -> `utilities.data_conversion.*`
- `data.text.*` -> `utilities.text_processing.*`

### 2) `control.flow` (8 nodes)

Current IDs:

- `control.flow.branch`
- `control.flow.compare`
- `control.flow.for_each`
- `control.flow.geometry_gate`
- `control.flow.geometry_merge`
- `control.flow.geometry_passthrough`
- `control.flow.geometry_switch`
- `control.flow.switch_select`

Recommended target:

- logical routing/comparison -> `math.logic.*`
- graph utility routing -> `utilities.flow_control.*`

### 3) `inputs.*` deferred survivors (10 nodes)

Current:

- `inputs.basic` (2)
- `inputs.minecraft` (2)
- `inputs.selectors` (4)
- `inputs.sources` (2)

Recommended target:

- interactive scalar/text UI inputs -> `input.*`
- domain selectors not in core modeling -> `utilities.selectors.*`
- file/text panels -> `utilities.fileio.*` or `utilities.text_processing.*`
- world-picked entity/sequence helpers -> `world.selection.*` (if promoted) or `utilities.assist.*` (if kept non-mainline)

### 4) `spatial.*` legacy survivors (32 nodes)

Current:

- `spatial.generators` (20)
- `spatial.analysis` (5)
- `spatial.instancing` (2)
- `spatial.points` (2)
- `spatial.voxel` (3)

Recommended target:

- direct block-output generators -> `output.execute.*` (bake/emit family)
- point and geometry analysis helpers -> `reference.*` / `geometry.*`
- instancing/scatter helpers -> `pattern.surface_volume_distribution.*`
- voxel booleans -> `geometry.boolean.*` (geometry domain) or `output.execute.*` (if treated as block sets)

### 5) `deferred.math` (1 node)

Current:

- `deferred.math.math_series`

Recommended target:

- keep deferred as-is until semantics are finalized, then migrate to `math.list_sequence.*` if aligned.

## Immediate Migration Batches (Low Risk First)

1. `data.lists.*` + `data.sequence.range/repeat` -> `math.list_sequence.*`
2. `data.conversion.*` + `data.text.*` -> `utilities.*` subdomains
3. `control.flow.compare` and branch/switch primitives -> `math.logic.*`
4. `spatial.points.*` + `spatial.analysis.*` -> `reference.*`/`geometry.*`
5. `spatial.generators.*_blocks` + `spatial.voxel.*` -> `output.execute.*`/`geometry.boolean.*`

## Suggested Rule for Ongoing Work

- No new node should be added under top-level prefixes: `spatial`, `data`, `inputs`, `control`, `deferred`.
- New nodes must be created directly under one of the 10 target framework families.
- Legacy IDs should be preserved only via alias/remap in `NodeRegistry`.
