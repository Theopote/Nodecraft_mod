# Geometry Combine — Node Language v1

**Status: PASSED / FROZEN** (Graph **V70**)

Geometry Combine v1 freezes the single structural combine node under `geometry.combine`, connection-aware transactional combine semantics, canonical 0/1/N output via `packGeometry`, leaf Count, Valid+Error ports, and `CompositeGeometryData` null rejection — completing the Composite entry/exit fail-closed loop started in V68–V69.

**Out of scope:** Union / Group / Flatten / Deconstruct nodes; `setInputCount` / `setNodeState` exact-Integer hardening; migrating all lossy `voxelize()` callers to `voxelizeStrict`; PreviewGeometryNode Error port.

## Inventory (order 0)

| Order | Display | Id | Effect |
|------:|---------|-----|--------|
| 0 | Combine Geometry | `geometry.combine.geometry` | `PURE` |

No `geometry.boolean.union` (legacy id remapped at V6). No additional combine-family nodes.

## Connection-aware combine

[`CombineGeometryNode`](../src/main/java/com/nodecraft/nodesystem/nodes/geometry/combine/CombineGeometryNode.java) uses [`OptionalPortDrive.isConnected`](../src/main/java/com/nodecraft/nodesystem/util/OptionalPortDrive.java) per `input_geometry_i`:

| Slot state | Behavior |
|------------|----------|
| Unconnected | Skipped |
| Connected + valid `GeometryData` | Collected (nested `CompositeGeometryData` flattened recursively) |
| Connected + null / wrong type | **Whole node invalid** — no partial Composite |

Zero connected valid leaves → `Valid=false`, `Geometry=null`, `Count=0`, actionable `Error`.

## Canonical 0 / 1 / N output

[`GeometryOutputUtils.packGeometry`](../src/main/java/com/nodecraft/nodesystem/util/GeometryOutputUtils.java):

| Leaf count | Output |
|------------|--------|
| 0 | `null` (node invalid) |
| 1 | Raw `GeometryData` (not `CompositeGeometryData(size=1)`) |
| 2+ | `CompositeGeometryData` |

**Count** = final flattened **leaf** count (not number of connected slots).

## CompositeGeometryData invariant

[`CompositeGeometryData`](../src/main/java/com/nodecraft/nodesystem/datatypes/CompositeGeometryData.java):

- Null members → reject (`Objects.requireNonNull`), not silent skip
- Shared [`flattenLeaves`](../src/main/java/com/nodecraft/nodesystem/datatypes/CompositeGeometryData.java) / `appendLeaves` used by constructor and Combine node
- Nested Composite recursive flatten retained (associative combine)

Aligns with V68 `packGeometry` and V69 strict composite voxelization.

## Ports

**Inputs:** `input_geometry_0` … `input_geometry_N-1` (`GEOMETRY`, dynamic `inputCount` 2–16)

**Outputs:** Geometry, Count (`INTEGER`), Valid (`BOOLEAN`), Error (`STRING`)

## Downstream

Combined output feeds Bounds / Voxel / Preview unchanged. Strict voxelization of Composite remains transactional (V69): child failure → whole FAILURE.

## Migration (V69 → V70)

Format bump only (Error port additive; order is catalog metadata). Identity `migrateV69ToV70`.

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryCombineLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.BooleanFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.Architectural*"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```
