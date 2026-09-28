# Node Language v1 — SDF

**Status: PASSED / FROZEN** (Graph **V93**)

Category: `geometry.sdf` (13 nodes). Type IDs remain historical `geometry.boolean.sdf_*` (ID rename deferred).

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

## Primitive contracts (aligned with geometry.primitives)

| Node | Rules |
|------|--------|
| Sphere | finite center; `radius > 0` finite |
| Box | finite center; half extents **each axis > 0** |
| Capsule | finite endpoints; `radius > 0`; non-zero axis length |
| Torus | ring torus only: `0 < minor < major` |

## Composition / deformation

- SDF Boolean: both SDFs required; Smooth K finite `>= 0`
- SDF Transform: scale finite and `> EPS` in the node (no constructor exception escape)
- Noise Displace / Domain Warp: Amplitude / Frequency / Seed connection-aware

## Query / mask

- Sample Point / Gradient: sampled scalars must be finite
- Sample Points: `POINT_LIST` → `DOUBLE_LIST` + `BOOLEAN_LIST`; any invalid entry fails the node (no silent drop); empty list → Count=0 success
- Blend Material Mask: `DOUBLE_LIST` in/out; Half Width finite `> 0` (no abs washout)

## SDF To Geometry bounds

| Min / Max | Behavior |
|-----------|----------|
| Both unconnected | Auto bounds (when Auto Bounds on) |
| Both connected valid | Explicit bounds |
| Exactly one connected | Error |
| Connected invalid | Error (never wash out to auto) |

## Difference auto bounds

[`SdfBoundsEstimator`](../src/main/java/com/nodecraft/nodesystem/util/SdfBoundsEstimator.java): Difference → **minuend** bounds (expanded by `smoothK` when soft). Same minuend-conservative rule as Geometry Difference.

## Out of scope (P2)

- Rename type IDs to `geometry.sdf.*`
- Failure numeric `0` vs `NaN` policy
- Additional SDF primitives

## Contract tests

`GeometrySdfLanguageContractTest` (Graph V93)
