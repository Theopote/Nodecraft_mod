# Node Language v1 — Point & Vector

## Spatial types

```text
POINT      = location (continuous geometry)
VECTOR     = direction / displacement (never a disguised position)
BLOCK_POS  = discrete Minecraft block coordinate
graph-facing angles = degrees only
```

## Producer / Deconstruct rule

```text
Construct Point          → POINT
Construct Block Position → BLOCK_POS
Block To Point           → POINT (explicit BLOCK_POS → POINT)

Deconstruct Point        → X/Y/Z DOUBLE
Deconstruct Block Position → X/Y/Z INTEGER
```

Producers emit semantic objects only — no duplicate Vector/X/Y/Z echo ports.

## Core operations

| Operation | Node id |
|-----------|---------|
| Construct Point | `reference.points.construct_point` |
| Translate Point (Point + Vector) | `reference.points.translate_point` |
| Vector Between Points | `reference.points.vector_between_points` |
| Move Point Along Direction | `reference.points.point_along_vector` |
| Distance Between Points | `reference.points.distance_between_points` |
| Closest Point (POINT_LIST) | `reference.points.closest_point` |

**Move Point Along Direction** always normalizes the direction vector before applying distance.
For raw displacement (`Point + Vector`), use **Translate Point**.

## Angles

`Angle Between Vectors` and `Slerp Vectors` expose angles in **degrees** only (`output_angle`, `output_signed_angle`).
No radians outputs on graph-facing ports.

## Deleted nodes (V19)

| Node | Replacement |
|------|-------------|
| Block To Vector | Block To Point → use point as needed; Vector Between Points for displacement |
| Closest Point To Object | Path: `geometry.curves.closest_point_on_path`; Surface/Geometry: future dedicated query nodes |

## Graph migration (V18→V19)

- Drops wires to removed position-as-VECTOR outputs
- Remaps `output_degrees` → `output_angle`, `output_angle_radians` → `output_angle`
- Removes `Block To Vector` and `Closest Point To Object` nodes from saved graphs
- Strips `normalizeDirection` from Move Point Along Direction state

## Vector producers (V21)

| Node | Outputs |
|------|---------|
| Construct Vector | `VECTOR` + Valid |
| Vector Input | `VECTOR` only |
| Deconstruct Vector | X/Y/Z DOUBLE + Valid |

Component echo ports were removed — use Deconstruct Vector.

## Graph migration (V20→V21)

- Drops wires from Construct Vector / Vector Input `output_x` / `output_y` / `output_z`

## Known limitations

- `Project Points To Plane` Distances — prefer `DOUBLE_LIST` (typed in List v1; wire when touching that node)
- Closest Point On Surface / Closest Point On Geometry — not yet implemented
- Bounding Box → Region — no dedicated conversion node; use explicit geometry pipeline
- Presets that used `normalizeDirection: false` on Move Point Along Direction need **Translate Point** for displacement semantics
- `Deconstruct Box Face` still emits untyped `LIST` for edges / index pairs — await typed `LINE_LIST` / `INTEGER_LIST`
- Full-repo `SpatialValueResolver.resolvePoint` coercion on typed POINT ports — staged audit remaining

## Box / face getters (V20)

| Node | Notes |
|------|-------|
| Get Box Corner | Corner is `POINT` |
| Get Box Face | KEEP — emits `BOX_FACE` |
| Get Face Edge | Slim: `LINE` + Found + Resolved Index |
| Deconstruct Face Edge | Start/End/Midpoint are `POINT`; Direction/Vector are displacement |

## World selection Point ↔ BlockPos (V20)

| Node | Input | Output |
|------|-------|--------|
| Block To Point | `BLOCK_POS` | `POINT` |
| Snap Point To Block | `POINT` | `BLOCK_POS` + Valid + Distance |
| Snap Point List To Blocks | `POINT_LIST` | `BLOCK_LIST` |
| Point To Block If Grid | `POINT` | `BLOCK_POS` only when grid-aligned |
