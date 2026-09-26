# Node Language v1 — Reference Frames

**Status: PASSED / FROZEN** (Graph **V47**)

Language unification for the eight canonical `reference.frames.*` nodes: canonical IDs,
shared `FrameUtils`, strict Construct semantics, Sphere X Hint, and orthonormal validation
at deconstruct boundaries — aligned with Frame/Plane v1 typed ports and fail-closed rules.

Related: [`node-language-v1-frame-plane.md`](./node-language-v1-frame-plane.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## FRAME invariant

```text
FRAME = Origin POINT + orthonormal right-handed X/Y/Z axes
|X| = |Y| = |Z| = 1
X perpendicular Y, Y perpendicular Z, Z perpendicular X
Z = X x Y
```

FRAME is position + orientation only — not scale, not shear.

## Inventory (8)

| order | Display name | Type id | Role |
|------:|--------------|---------|------|
| 0 | Face Center Frame | `reference.frames.frame_from_face` | Frame at box face center |
| 1 | Sphere Surface Frame | `reference.frames.sphere_surface_frame` | Tangent frame on sphere surface |
| 2 | World Frame | `reference.frames.world_frame` | Identity world frame |
| 3 | Construct Frame | `reference.frames.construct_frame` | Build frame from origin + X/Y |
| 4 | Frame From Plane | `reference.frames.frame_from_plane` | Tangent frame on plane |
| 5 | Transform Frame | `reference.frames.transform_frame` | Translate + rotate frame |
| 6 | Deconstruct Frame | `reference.frames.deconstruct_frame` | Split one FRAME |
| 7 | Deconstruct Frames | `reference.frames.deconstruct_frames` | Split FRAME_LIST |

Legacy id `reference.frames.frame_along_surface` removed at V47 (no alias migration).

## Shared FrameUtils

Single helper class: `com.nodecraft.nodesystem.util.FrameUtils`

Public API:
- `EPS`, `isFinite`, `isUsableAxis`, `areParallel`
- `resolvePoint`, `resolveVector` (delegate to `SpatialValueResolver`)
- `normalizedDirection`
- `fromPlane(plane, xHint)` — Z = plane normal; X from hint + cardinal fallback
- `fromNormal(origin, normal, xHint)` — tangent frame on any surface normal

## Construct Frame

Inputs: Origin `POINT`, X Axis `VECTOR`, Y Axis `VECTOR` (all optional)

Outputs: Frame `FRAME`, Valid `BOOLEAN`

Documented defaults when unconnected:
- Origin -> world origin `(0,0,0)`
- X Axis -> world `+X`
- Y Axis -> world `+Y`
- Z computed as `X x Y` via `FrameData.orthonormal`

Rules:
- Connected non-finite / zero axis -> `Valid=false`
- **Both X and Y wired and parallel** -> `Valid=false` (strict; no cardinal fallback)
- Otherwise -> orthonormal right-handed frame

## Frame From Plane

Uses `FrameUtils.fromPlane`. Optional X Hint `VECTOR` projected onto the plane;
deterministic cardinal fallback when hint is missing or parallel to normal.

## Sphere Surface Frame

Inputs: Sphere `SPHERE`, Point `POINT`, optional X Hint `VECTOR`

Outputs: Frame `FRAME`, Surface Point `POINT`, Normal `VECTOR`, Valid `BOOLEAN`

Sphere-only — not a generic surface frame.

Algorithm: project point to sphere surface, outward normal, then
`FrameUtils.fromNormal(origin, normal, xHint)`.

## Transform Frame

Inputs: Frame `FRAME`, Translation `VECTOR`, Rotation X/Y/Z `DOUBLE` (degrees)

Outputs: Frame `FRAME`, Valid `BOOLEAN`

No scale input. Output axes are unit length.

## Deconstruct Frame / Deconstruct Frames

Deconstruct Frame input: Frame `FRAME`

Deconstruct Frames input: Frames `FRAME_LIST`

Outputs (single): Origin, X/Y/Z axes, Plane, Valid

Outputs (list): Origins, X/Y/Z axes, Planes, Count, Valid

Boundary rule: each entry must `orthonormalized()` successfully or the node fails closed
(`Valid=false`, empty outputs / Count=0).

Empty input, mixed invalid list entries, and degenerate frames all fail closed.

## Deferred (P2)

- FrameData constructor hardening at every producer boundary
- Generic surface frames beyond Sphere
