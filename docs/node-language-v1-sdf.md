# Node Language v1 — SDF

**Status: PASSED / FROZEN** (historical Graph **V93** residue; `GraphFormatVersion.CURRENT` is stamp-only **1**)

Category: `geometry.sdf` (13 nodes). Type IDs remain historical `geometry.boolean.sdf_*` (ID rename deferred).

## Sign convention (frozen)

`distance < 0` inside; `distance = 0` surface; `distance > 0` outside. Query inside = `distance <= 0`. Uniform positive scale: `sample(world) = source(inverse(world)) * scale`.

## Language contract

Every SDF node exposes:

- `Valid` (`BOOLEAN`)
- `Error` (`STRING`)

Optional numeric / spatial inputs use connection-aware resolution via [`SdfInputUtils`](../src/main/java/com/nodecraft/nodesystem/util/SdfInputUtils.java):

| Wire state | Result |
|------------|--------|
| Unconnected | Property / default |
| Connected valid | Connected value |
| Connected invalid / null | Fail closed (`Valid=false` + Error) |

Invalid objects `null`, lists `List.of()`, graph DOUBLE diagnostics **NaN**.

## Canonical SDF value types

[`SdfFieldValidator`](../src/main/java/com/nodecraft/nodesystem/util/SdfFieldValidator.java): constructors **reject** illegal payloads (no silent `Math.max`). Direct Java construction of NaN radius / non-rotation matrices throws.

## Primitive contracts (aligned with geometry.primitives)

| Node | Rules |
|------|--------|
| Sphere | finite center; `radius > 0` finite |
| Box | finite center; half extents **each axis > 0** (no abs washout) |
| Capsule | finite endpoints; `radius > 0`; **finite** axis (`safeSubtract` / `safeLength`) |
| Torus | ring torus only: `0 < minor < major` |

## Composition / deformation

- SDF Boolean: both SDFs required; Smooth K finite `>= 0`; Operation is `BooleanSdfData.Operation` (unknown saved names ignored, not mapped to UNION)
- SDF Transform: scale finite and `> EPS`; translation/Euler finite; matrix must be finite orthonormal rotation
- Noise Displace / Domain Warp: Amplitude `>= 0`, Frequency `> 0`, Seed exact integer, **Offset finite**
- SDF To Geometry padding setter: finite `>= 0` (no NaN `Math.max`)

## Query / mask

- Sample Point / Gradient: sampled scalars must be finite; Gradient uses `safeNormalize` and finite probe points; invalid distance **NaN**
- Sample Points: `POINT_LIST` → `DOUBLE_LIST` + `BOOLEAN_LIST`; any invalid entry fails; size `<= MAX_SDF_SAMPLE_POINTS` (`MAX_FIELD_SAMPLE_POINTS`); empty list → Count=0 success
- Blend Material Mask: exact `List<Double>` (`instanceof Double`); Half Width finite `> 0`

## SDF To Geometry bounds

| Min / Max | Behavior |
|-----------|----------|
| Both unconnected | Auto bounds (when Auto Bounds on) |
| Both connected valid | Explicit bounds |
| Exactly one connected | Error |
| Connected invalid | Error (never wash out to auto) |

Voxel count / volume budgets stay in [`GeometryVoxelizer`](../src/main/java/com/nodecraft/nodesystem/util/GeometryVoxelizer.java). Auto bounds fail if [`SdfBoundsEstimator`](../src/main/java/com/nodecraft/nodesystem/util/SdfBoundsEstimator.java) returns null (non-finite AABB or expression over `MAX_SDF_EXPRESSION_DEPTH`).

## Difference auto bounds

Difference → **minuend** bounds (expanded by `smoothK` when soft). Same minuend-conservative rule as Geometry Difference.

## Out of scope (deferred)

- Rename type IDs to `geometry.sdf.*`
- Replace sin-hash noise with `RandomOps.valueNoise3`
- Additional SDF primitives

## Contract tests

`GeometrySdfLanguageContractTest`
