# Node Language v1 — Reference Planes

**Status: PASSED / FROZEN** (Graph **V48**)

Language unification for the seven canonical `reference.planes.*` nodes: PLANE invariant,
shared `PlaneUtils`, strict World Plane Origin semantics, canonical validation at query/deconstruct
boundaries — aligned with Reference Frames v1 and typed spatial ports.

Related: [`node-language-v1-reference-frames.md`](./node-language-v1-reference-frames.md),
[`node-language-v1-frame-plane.md`](./node-language-v1-frame-plane.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## PLANE invariant

```text
PLANE = finite origin POINT + normalized non-zero normal VECTOR
|normal| = 1
```

PLANE carries origin + normal only — no tangent X/Y (those belong to FRAME).

## Inventory (7)

| order | Display name | Type id | Role |
|------:|--------------|---------|------|
| 0 | World Plane | `reference.planes.world_plane` | Preset XY/YZ/XZ world planes |
| 1 | Construct Plane | `reference.planes.construct_plane` | Explicit origin + normal |
| 2 | Construct Plane From Points | `reference.planes.plane_from_points` | Plane through three points |
| 3 | Box Face To Plane | `reference.planes.box_face_plane` | Box face to supporting plane |
| 4 | Offset Plane | `reference.planes.offset_plane` | Signed offset along normal |
| 5 | Distance Point To Plane | `reference.planes.distance_point_to_plane` | Absolute + signed distance |
| 6 | Deconstruct Plane | `reference.planes.deconstruct_plane` | Split one PLANE |

Legacy id `reference.planes.block_face_plane` removed at V48 (no alias migration).

## Shared PlaneUtils

Single helper class: `com.nodecraft.nodesystem.util.PlaneUtils`

Public API:
- `EPS`, `isFinite`, `isUsableNormal`
- `fromOriginNormal(origin, normal)` — delegates to `PlaneData.canonical`
- `fromThreePoints(a, b, c)` — scale-invariant collinearity check

Point/vector resolution uses `SpatialValueResolver` (not duplicated in PlaneUtils).

## PlaneData canonical API

- `PlaneData.canonical(origin, normal)` — finite origin + usable normal
- `PlaneData.fromEquation(Vector4d)` — normalizes (a,b,c,d) by |normal|
- `plane.normalized()` — validate + return unit-normal copy (symmetric with `FrameData.orthonormalized()`)

## World Plane vs Construct Plane

**World Plane** — convenient preset producer:
- Optional Origin `POINT`; unconnected → property fallback
- Connected invalid Origin → `Valid=false` (no silent property fallback)
- Outputs: Plane `PLANE`, Valid `BOOLEAN`

**Construct Plane** — explicit construction:
- Required Origin `POINT` + Normal `VECTOR` (no implicit defaults)
- Invalid inputs → `Valid=false`

## Plane From Points

Normal = normalize((B-A) x (C-A))

Point order determines normal direction; swapping B and C flips the normal.

Collinearity: `|AB x AC|^2 <= EPS * |AB|^2 * |AC|^2` → invalid.

## Signed distance semantics

Used by Distance Point To Plane and Offset Plane:

```text
signedDistance > 0  → point on normal-facing side
signedDistance < 0  → point opposite the normal
signedDistance = 0  → point on the plane
```

Invalid inputs → Distance=NaN, Signed Distance=NaN, Valid=false (never 0).

## Deconstruct / query boundaries

Deconstruct Plane, Distance Point To Plane, and Offset Plane call `plane.normalized()`
before computing. Degenerate planes fail closed.

Deconstruct outputs canonical origin (POINT) and unit normal (VECTOR).

## Deferred (P2)

- Privatize `PlaneData` public constructors (datatypes-layer hardening)
- Broader migration of non-reference nodes to `PlaneData.canonical()` at every producer boundary
