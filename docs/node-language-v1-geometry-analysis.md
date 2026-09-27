# Geometry Analysis — Node Language v1

**Status: PASSED / FROZEN** (Graph **V67**)

Geometry Analysis v1 freezes the language for the 2 `geometry.analysis` nodes.
Core rule: `BOUNDING_BOX` is a **continuous** geometric AABB; `REGION` / `BLOCK_POS` stay in the discrete Minecraft cell domain.

**Out of scope:** retargeting primitive / architectural deconstruct nodes still on `GeometryVoxelizer` bounds (follow-up).

## Inventory (order 0–1)

| Order | Display | Id | Effect |
|------:|---------|-----|--------|
| 0 | Block Bounds | `geometry.analysis.block_bounds` | `PURE` |
| 1 | Geometry Bounds | `geometry.analysis.geometry_bounds` | `PURE` |

**Renamed from:** `geometry.boolean.bounding_box` / `geometry.boolean.geometry_bounds`.

## BoundingBoxData

- Factory `create(min, max)` → `null` when null / non-finite / inverted (`min > max` on any axis).
- Closed-interval membership `[min, max]`.
- Helpers: `center()`, `size()`, `volume()`, `union()`, `intersection()` (non-overlap → `null`).

## Block Bounds

| Port | Type |
|------|------|
| Blocks | BLOCK_LIST optional |
| Region | REGION optional |
| Bounding Box | BOUNDING_BOX (continuous cell envelope) |
| Region | REGION |
| Min Block / Max Block | BLOCK_POS |
| Center | POINT (midpoint of continuous envelope) |
| Size X/Y/Z | INTEGER |
| Volume | DOUBLE (`long`/`double` product) |
| Valid / Error | BOOLEAN / STRING |

Rules:

- Exactly one source (connection-aware). Both connected → `Valid=false`. Neither → invalid.
- Connected-invalid Blocks → fail closed (no Region fallback). Empty Blocks → `Valid=false`.
- Strict `BlockListUtils.resolveStrictBlockList` (not `instanceof BlockPosList` alone).
- Cell envelope: block `(x,y,z)` → continuous `[x,y,z]→[x+1,y+1,z+1]`.
- Size axes fail closed if span not representable in `int`.

## Geometry Bounds

| Port | Type |
|------|------|
| Geometry | GEOMETRY |
| Bounding Box | BOUNDING_BOX |
| Min / Max / Center | POINT |
| Size X/Y/Z | DOUBLE |
| Volume | DOUBLE |
| Valid / Error | BOOLEAN / STRING |

Rules:

- Resolves via `GeometryBoundsResolver` only — **no** `GeometryVoxelizer` / BlockPos flooring.
- Zero-thickness AABB (size axis 0, volume 0) allowed when finite.
- Boolops: Composite = AABB union; Difference = minuend (conservative); Intersection = AABB ∩ AABB (non-overlap → invalid).
- No Region / BLOCK_POS / INTEGER size ports.

## Migration (V66 → V67)

- Rename `geometry.boolean.bounding_box` → `geometry.analysis.block_bounds`
- Rename `geometry.boolean.geometry_bounds` → `geometry.analysis.geometry_bounds`
- Block Bounds port remap: `input_coordinates`→`input_blocks`, `output_min_corner`→`output_min_block`, `output_max_corner`→`output_max_block`
- Drop wires to removed Geometry Bounds ports: `output_region`, `output_min_corner`, `output_max_corner`, `output_center`

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GeometryAnalysisLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.BooleanFamilyContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```
