# Node Language v1 — SDF

**Status: PASSED / FROZEN** (historical Graph **V93** residue; `GraphFormatVersion.CURRENT` is stamp-only **1**)

Category: `geometry.sdf` (13 nodes). Type IDs remain historical `geometry.boolean.sdf_*` (ID rename deferred). All nodes are `PURE`.

## Language contract

Every SDF node exposes:

- `Valid` (`BOOLEAN`)
- `Error` (`STRING`)

Invalid objects `null`, lists `List.of()`, graph DOUBLE diagnostics **NaN**.

Optional numeric / spatial inputs use connection-aware resolution via [`SdfInputUtils`](../src/main/java/com/nodecraft/nodesystem/util/SdfInputUtils.java):

| Wire state | Result |
|------------|--------|
| Unconnected | Property / default |
| Connected valid | Connected value |
| Connected invalid / null | Fail closed (`Valid=false` + Error) |

## Sign convention (frozen)

`distance < 0` inside; `distance = 0` surface; `distance > 0` outside. Query **inside** = `distance <= 0`. Uniform positive scale: `local = Rᵀ (world − t) / s`, `distance = source(local) * s`.

## Canonical SDF value types

Constructors **reject** illegal payloads (no silent `Math.max` / frequency floor). Direct Java construction of NaN radius, non-positive extents, null boolean operands, non-orthonormal transform matrices, or non-finite noise offsets throws. Nodes fail closed before `new`.

Capsule sampling uses `safeSubtract` / `safeLength` (finite endpoints are not enough if the axis overflows).

## Primitive contracts (aligned with geometry.primitives)

| Node | Rules |
|------|--------|
| Sphere | finite center; `radius > 0` finite |
| Box | finite center; half extents **each axis > 0** (no abs washout) |
| Capsule | finite endpoints; `radius > 0`; finite non-zero axis (`requirePositiveAxis`) |
| Torus | ring torus only: `0 < minor < major` |

## Composition / deformation

- SDF Boolean: both SDFs required; Smooth K finite `>= 0`; **Operation** is `BooleanSdfData.Operation` (unknown saved string → `Valid=false`, not UNION)
- SDF Transform: scale finite and `> EPS`; translation / Euler finite; matrix ctor requires orthonormal rotation
- Noise Displace / Domain Warp: Amplitude `>= 0`, Frequency `> 0`, Seed exact integer, **Offset finite**
- SDF To Geometry Padding setter: ignore non-finite / `< 0`

## Query / mask

- Sample Point / Gradient: sampled scalars must be finite; Gradient probes must be finite; `safeNormalize` or `Valid=false`; invalid Distance is **NaN**
- Sample Points: `POINT_LIST` → `DOUBLE_LIST` + `BOOLEAN_LIST`; any invalid entry fails; size `<= MAX_SDF_SAMPLE_POINTS` (`MAX_FIELD_SAMPLE_POINTS`); empty list → Count=0 success
- Blend Material Mask: `DOUBLE_LIST` = `List<Double>` only; Half Width finite `> 0`

## SDF To Geometry bounds

| Min / Max | Behavior |
|-----------|----------|
| Both unconnected | Auto bounds (when Auto Bounds on) |
| Both connected valid | Explicit bounds |
| Exactly one connected | Error |
| Connected invalid | Error (never wash out to auto) |

Voxel count / volume budgets stay in [`GeometryVoxelizer`](../src/main/java/com/nodecraft/nodesystem/util/GeometryVoxelizer.java) — not duplicated here.

## Difference auto bounds

[`SdfBoundsEstimator`](../src/main/java/com/nodecraft/nodesystem/util/SdfBoundsEstimator.java): Difference → **minuend** bounds (expanded by `smoothK` when soft). `AxisAlignedBounds.isValid` requires finite min/max. Transform bounds use overflow-safe midpoint/extent (non-finite → `null`). Walk caps: `MAX_SDF_EXPRESSION_DEPTH` (= `MAX_GEOMETRY_EXPRESSION_DEPTH`) and `MAX_SDF_NODE_COUNT`.

## Out of scope (deferred)

- Rename type IDs to `geometry.sdf.*`
- Replace sin-hash noise with `RandomOps.valueNoise3`
- Additional SDF primitives

## Contract tests

`GeometrySdfLanguageContractTest`
