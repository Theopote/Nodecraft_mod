# Node Language v1 — Frame & Plane

## PLANE

```text
PLANE = Origin POINT + Normal VECTOR
```

- Plane has a construction/reference origin and a normal direction
- Plane does **not** include stable in-plane X/Y orientation
- Plane is **not** a Frame
- Plane has no scale and no block-grid semantics on outputs

## FRAME

```text
FRAME = Origin POINT + orthonormal right-handed X/Y/Z axes
```

Requirements:

```text
|X| = |Y| = |Z| = 1
X ⟂ Y, Y ⟂ Z, Z ⟂ X
Z = X × Y
```

FRAME represents **position + orientation only** — not scale, not shear.

All FRAME producers must output orthonormal right-handed frames via `FrameData.orthonormal(...)`.

## Producer / Deconstruct rule

Producers emit semantic objects:

```text
FRAME / FRAME_LIST
PLANE
```

Deconstruct nodes expand internal fields:

```text
FRAME      → Deconstruct Frame
FRAME_LIST → Deconstruct Frames
PLANE      → Deconstruct Plane
```

Do not duplicate Origin/X/Y/Z/Plane on every producer.

## Plane vs Frame

| Type | Contains | Missing |
|------|----------|---------|
| PLANE | Origin, Normal | Stable tangent orientation |
| FRAME | Origin, X, Y, Z | — |

```text
Plane → Frame requires X Hint (or deterministic fallback)
Frame → Plane via Deconstruct Frame (origin + Z normal)
```

## Core nodes

### Plane

| Node | id |
|------|-----|
| World Plane | `reference.planes.world_plane` |
| Construct Plane | `reference.planes.construct_plane` |
| Construct Plane From Points | `reference.planes.plane_from_points` |
| Deconstruct Plane | `reference.planes.deconstruct_plane` |
| Offset Plane | `reference.planes.offset_plane` |
| Box Face To Plane | `reference.planes.block_face_plane` |

### Frame

| Node | id |
|------|-----|
| World Frame | `reference.frames.world_frame` |
| Construct Frame | `reference.frames.construct_frame` |
| Deconstruct Frame | `reference.frames.deconstruct_frame` |
| Deconstruct Frames | `reference.frames.deconstruct_frames` |
| Frame From Plane | `reference.frames.frame_from_plane` |
| Transform Frame | `reference.frames.transform_frame` |

### Specialized producers

| Node | id | Notes |
|------|-----|-------|
| Path Frames | `pattern.linear.path_instances` | Frames + path sampling (Points, Tangents) |
| Face Center Frame | `reference.frames.frame_from_face` | Frame + Center |
| Sphere Surface Frame | `reference.frames.frame_along_surface` | Frame + Surface Point + Normal |

## Transform Frame

- Input: `FRAME` + Translation + Rotation XYZ (degrees)
- No Scale — scale belongs on geometry transform nodes
- No decomposed Origin/X/Y/Z inputs
- Output: `FRAME` + `Valid` only

## Construct Frame

- Input: Origin, X Axis, Y Axis (Z computed as X × Y)
- No Z Axis input
- Output: `FRAME` + `Valid`

## Transform Points by Frames

- Input: `POINT_LIST` + `FRAME_LIST`
- No parallel Origins/X/Y/Z lists
- Output: `POINT_LIST` + Count + Valid

Typical chain:

```text
Path Frames → FRAME_LIST → Transform Points by Frames
```

## Resample / sampling

Resample Path remains the only arc-length sampling node for paths. Path Frames uses existing path vertices (parallel transport).

## Graph migration (V17→V18)

Removed producer ports are dropped from saved graphs. Old decomposed-frame connections to Transform Points by Frames cannot be auto-synthesized — repair manually using `FRAME_LIST`.

## Known limitations

- Generic Surface Frame not implemented (no unified Surface protocol)
- FRAME has no scale/shear by design
- PLANE has no tangent orientation
- Box Face metadata (Center, Index, Corners) deferred to future Deconstruct Box Face
- Old decomposed-frame graph connections may require manual repair
