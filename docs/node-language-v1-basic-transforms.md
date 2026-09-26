# Node Language v1 — Basic Transforms

**Status: PASSED / FROZEN** (Graph **V52**)

Continuous Geometry / Point / BoxFace transforms only. Block-grid coordinate transforms live in
`transform.placement`. Shear lives in `transform.deformations`.

Related: [`node-language-v1-reference-vectors.md`](./node-language-v1-reference-vectors.md),
[`node-language-v1-reference-planes.md`](./node-language-v1-reference-planes.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Product boundary

```text
basic_transforms = continuous Geometry / Point / BoxFace transforms
BLOCK_LIST + round-snap = placement (not main modeling chain)
Scale > 0; Mirror is separate
Optional ports: connection-aware fail-closed
POINT_LIST: strict fail-closed
Composite transform/mirror: transactional
VECTOR in/out: resolver + VectorData
TRS order: Scale → RotateXYZ(degrees) → Translate (origin-based)
Frames×Points: cartesian product, Frame-major
```

## Inventory (9)

| order | Display name | Type id |
|------:|--------------|---------|
| 0 | Move Geometry | `transform.basic_transforms.move_geometry` |
| 1 | Rotate Geometry Around Axis | `transform.basic_transforms.rotate_geometry_axis` |
| 2 | Scale Geometry Around Point | `transform.basic_transforms.scale_geometry_point` |
| 3 | Transform Geometry | `transform.basic_transforms.transform_geometry` |
| 4 | Mirror Geometry About Plane | `transform.basic_transforms.mirror_geometry_plane` |
| 5 | Mirror Point List About Plane | `transform.basic_transforms.mirror_point_list_plane` |
| 6 | Transform Points by Frames | `transform.basic_transforms.transform_by_frames` |
| 7 | Offset Box Face | `transform.basic_transforms.offset_face` |
| 8 | Inset Box Face | `transform.basic_transforms.inset_face` |

Moved out at V52:

| Legacy id | New id |
|-----------|--------|
| `…offset_coordinate` / `offset_coordinates` / `rotate_coordinates` / `scale_coordinates` / `mirror_coordinates` | `transform.placement.*_block_position(s)` (orders 3–7; renamed from Coordinate at V54) |
| `…shear` | `transform.deformations.shear_point_list` (order 10) |
| `…mirror_vector_list_plane` | `…mirror_point_list_plane` (rename in-family) |

## Optional-drive contract

```text
unconnected              → property fallback
connected + valid input  → input override
connected + null/invalid → fail closed (no property fallback)
```

Shared helper: `OptionalPortDrive`.

## Composite transactional transform / mirror

Any null child under `CompositeGeometryData` causes the whole Composite transform/mirror to return
`null` (fail closed). Empty composites are invalid.

## Scale and Mirror

- Scale factors must be **strictly greater than zero**.
- Reflection is only via Mirror nodes — negative scale is rejected.

## Point lists

- Inputs use `PointUtils.resolveStrictPointList` (no Skipped Count).
- Mixed / unparsable lists → empty + `Valid=false`.

## Box Face slim ports

Offset / Inset emit only `Face` + `Valid`. Distance is required and must be finite.
Positive inset requires `distance < min(width, height) / 2`; negative outset accepts any finite value.
Both reject non-finite corners with `Face=null` / `Valid=false` (no exception).

## Transform Points by Frames

Cartesian product, **Frame-major**:

```text
Frame0 × all local points, then Frame1 × all local points, …
```

Frames are orthonormalized before application (axis length does not scale points).

## Migration (V51 → V52)

- Remap five block-grid type ids → `transform.placement.*`
- Remap `mirror_vector_list_plane` → `mirror_point_list_plane`
- Remap `basic_transforms.shear` → `deformations.shear_point_list`
- Drop wires from removed ports (face echoes, Offset Coordinate X/Y/Z, Skipped Count)

No publishing-time aliases (pre-release remap only).
