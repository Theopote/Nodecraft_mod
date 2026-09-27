# Geometry Boolean — Node Language v1

**Status: PASSED / FROZEN** (Graph **V69**)

Geometry Boolean v1 freezes the two deferred **voxel-grid** boolean nodes under `geometry.boolean`, plus the shared strict voxel evaluation protocol used by Difference / Intersection / Composite and Boolean Preview.

**Out of scope:** analytic BRep boolean, Union node, moving SDF Boolean into this category, Valid on deferred GeometryData wrappers, full `VoxelizeGeometryNode` Valid/Error migration.

## Inventory (order 0–1)

| Order | Display | Id | Effect |
|------:|---------|-----|--------|
| 0 | Difference | `geometry.boolean.difference` | `PURE` |
| 1 | Intersection | `geometry.boolean.intersection` | `PURE` |

No `geometry.boolean.union` (use `geometry.combine.geometry`). SDF Boolean remains `geometry.boolean.sdf_boolean` under category **`geometry.sdf`**.

## Node Valid vs voxel SUCCESS

| Layer | Meaning |
|-------|---------|
| Node `Valid` | Deferred expression constructed (`DifferenceGeometryData` / `IntersectionGeometryData`) |
| `GeometryVoxelizationResult.SUCCESS` | Operand(s) evaluated; blocks may be empty (legal empty) |
| Non-SUCCESS status | Evaluation failed — never treat as legal empty |

## Strict voxel evaluation

[`GeometryVoxelizer.voxelizeStrict`](../src/main/java/com/nodecraft/nodesystem/util/GeometryVoxelizer.java) → [`GeometryVoxelizationResult`](../src/main/java/com/nodecraft/nodesystem/util/GeometryVoxelizationResult.java):

- Status: `SUCCESS` / `UNSUPPORTED` / `OVER_BUDGET` / `INVALID_BOUNDS` / `CHILD_FAILURE`
- Hard cap: [`GenerationLimits.MAX_GEOMETRY_VOXELS`](../src/main/java/com/nodecraft/nodesystem/util/GenerationLimits.java)
- Composite / Diff / Inter: transactional — child failure → whole FAILURE (no partial union/subtract)
- Empty cutter SUCCESS → Difference = base (legal)
- Disjoint ∩ → SUCCESS + empty (legal)
- Failed cutter / operand → FAILURE (not empty SUCCESS)

Legacy `voxelize(...)` is lossy (FAILURE → empty list) for non-migrated callers. Boolean Preview uses **strict** only.

## Ports

**Difference:** Base / Cutter `GEOMETRY` → Geometry, Valid, Error  
**Intersection:** Left / Right `GEOMETRY` → Geometry, Valid, Error  

Missing / non-geometry → `Valid=false` + actionable Error. No property fallback.

## Bounds (unchanged from V67)

- Difference AABB = minuend (conservative)
- Intersection AABB = AABB ∩ (null when incomplete **or** disjoint — voxel path still distinguishes via strict child eval)

## Migration (V68 → V69)

Format bump only (Error ports additive; catalog order metadata).

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryBooleanLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.BooleanFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```
