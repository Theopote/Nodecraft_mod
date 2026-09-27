# Node Language v2 — Reference Planes

**Status: PASSED / FROZEN** (Graph **V86**; V48 remains historical v1)

Language modernization for the seven canonical `reference.planes.*` nodes:
Valid+Error on all nodes, OptionalPortDrive on World Plane Origin and Offset Distance,
BoxFaceValidator on Box Face To Plane, strict DOUBLE distance semantics, and
canonical producer/consumer boundaries.

Related: [`node-language-v1-reference-planes.md`](./node-language-v1-reference-planes.md),
[`node-language-v2-reference-frames.md`](./node-language-v2-reference-frames.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## PLANE invariant

Unchanged from v1:

```text
PLANE = finite construction origin POINT + normalized non-zero normal VECTOR
|normal| = 1
```

PLANE carries origin + normal only — no tangent X/Y (those belong to FRAME).

**Construction origin** is the point supplied when the plane was built (`plane.getPoint()`),
not an equation-derived closest point. Frame From Plane (V85) uses this origin as frame origin.

## Inventory (orders 0–6)

| Order | Display name | Type id | Valid+Error |
|------:|--------------|---------|:-----------:|
| 0 | World Plane | `reference.planes.world_plane` | yes |
| 1 | Construct Plane | `reference.planes.construct_plane` | yes |
| 2 | Construct Plane From Points | `reference.planes.plane_from_points` | yes |
| 3 | Box Face To Plane | `reference.planes.box_face_plane` | yes |
| 4 | Offset Plane | `reference.planes.offset_plane` | yes |
| 5 | Distance Point To Plane | `reference.planes.distance_point_to_plane` | yes |
| 6 | Deconstruct Plane | `reference.planes.deconstruct_plane` | yes |

## Core rules

1. **Valid + Error** on all seven nodes (including World Plane).
2. **OptionalPortDrive** — World Plane Origin: unconnected → property XYZ; connected valid → wire; connected invalid → fail. Offset Distance: unconnected → `0.0`; connected exact finite DOUBLE → wire; connected invalid → fail.
3. **Box Face To Plane** — `BoxFaceValidator.validate(face)` before plane construction (shared with V75/V85).
4. **Construct Plane** — required Origin + Normal; explicit error strings for missing/invalid inputs.
5. **Plane From Points** — per-point validation; scale-relative collinearity via `PlaneUtils.fromThreePoints`; point order determines normal orientation.
6. **Offset Plane** — input plane canonicalized via `normalized()` before offset; output via `PlaneUtils.fromOriginNormal` (rejects overflow origin).
7. **Distance Point To Plane** — plane canonicalized before math; signed distance positive = normal side; failure → NaN distances + Valid=false + Error.
8. **Deconstruct Plane** — `plane.normalized()` boundary; outputs canonical unit normal and construction origin.
9. **PlaneUtils / PlaneData public API unchanged** — no PLANE_LIST or list caps in this family.

## Migration (V85 → V86)

Format bump only. Error ports additive; node IDs and port IDs unchanged; no wire remap.
