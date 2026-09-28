# Geometry Voxel — Language v1

**Status: PASSED / FROZEN** (Graph **V95**)

Category: `geometry.voxel` (1 node: **Voxelize Geometry**). Converts `GEOMETRY` / `GEOMETRY` tree into `BLOCK_LIST` / `DATA_TREE`. **PURE** — does not write the Minecraft world; use **Apply Changes** after Material / Block State.

## Node

| Order | Display | Id |
|------:|---------|-----|
| 0 | Voxelize Geometry | `geometry.voxel.voxelize_geometry` |

## Strict evaluation contract

Canonical path: [`GeometryVoxelizer.voxelizeStrict`](src/main/java/com/nodecraft/nodesystem/util/GeometryVoxelizer.java).

| Outcome | Valid | Blocks | Region | Error / Status |
|---------|-------|--------|--------|----------------|
| Success (incl. legal empty) | true | result | bounding region | `""` / `SUCCESS` |
| Failure | false | `[]` | `null` | actionable message / status enum name |

**Rule:** `SUCCESS + empty ≠ FAILURE`. Never use lossy `voxelize()` on the public node.

## Inputs (connection-aware)

| Port | When connected | Behavior |
|------|----------------|----------|
| Geometry Tree | yes | **Authoritative.** Invalid tree → fail. Empty tree → success empty. No fallback to Geometry. |
| Geometry | Tree not connected | Required valid `GEOMETRY`; missing/invalid → fail |

Tree evaluation is **transactional**: any non-`GeometryData` item or any child voxelization failure fails the whole node (no partial output).

## Fill Geometry

| Value | Meaning |
|-------|---------|
| true | Solid voxels |
| false | Boundary shell of the **final** voxelized geometry (where supported) |

Boolean Difference / Intersection with `Fill=false`: **solid CSG first**, then `extractShell` on the result — not shell-of-operands then CSG.

## Budget layers

1. **Leaf bounds:** bounding volume ≤ `MAX_GEOMETRY_VOXELS` (262_144)
2. **Aggregate merge:** Composite and Geometry Tree flattened output ≤ `MAX_GEOMETRY_VOXELS`
3. **Tree items:** total geometry items ≤ `MAX_GEOMETRY_INSTANCES`

## Composite vs Boolean Union

`CompositeGeometryData` is **structural grouping** (union of child voxel sets). It is not geometric CSG Union — overlapping internal faces may remain. Use Boolean nodes for true CSG.

## SDF evaluation

Non-finite SDF sample → `EVALUATION_FAILURE` (fail closed), not silent outside voxels.

## Out of scope

- World write on Voxelize Geometry
- Renaming Fill Geometry UI
- Composite-as-CSG-Union

## Contract tests

`GeometryVoxelLanguageContractTest` (Graph V95); Boolean shell cases in `GeometryBooleanLanguageContractTest`.
