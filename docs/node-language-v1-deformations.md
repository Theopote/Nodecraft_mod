# Node Language v1 — Deformations

**Status: PASSED / FROZEN** (Graph **V53**)

Point-list and SDF/geometry morphs under `transform.deformations` (11 nodes). Upgraded to the
same strict typed-list / OptionalPortDrive / exact INTEGER language frozen by Points / Vectors /
Basic Transforms (V49–V52).

Related: [`node-language-v1-basic-transforms.md`](./node-language-v1-basic-transforms.md),
[`node-language-v1-reference-vectors.md`](./node-language-v1-reference-vectors.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Product boundary

```text
deformations = point-list + SDF/geometry morphs (11 nodes, order 0-10)
POINT_LIST / VECTOR_LIST: strict fail-closed
Optional ports: OptionalPortDrive
INTEGER: exact Integer
Length/Radius/Power > 0; Frequency >= 0; Taper scales >= 0
no silent EPS/abs/minScale/swap/intValue repair
Geometry-connected precedence over SDF
budgets: GenerationLimits (relax points, deform voxels)
```

## Inventory (11)

| order | Display name | Type id |
|------:|--------------|---------|
| 0 | Twist Point List | `transform.deformations.twist` |
| 1 | Bend Point List | `transform.deformations.bend` |
| 2 | Taper Point List | `transform.deformations.taper` |
| 3 | Shear Point List | `transform.deformations.shear_point_list` |
| 4 | Noise Displace Point List | `transform.deformations.noise_displace` |
| 5 | Spherical Displace | `transform.deformations.spherical_displace` |
| 6 | Path Attract Point List | `transform.deformations.curve_attract` |
| 7 | Relax Point List | `transform.deformations.relax_points` |
| 8 | Lattice Deform Point List | `transform.deformations.lattice_deform` |
| 9 | Twist Geometry | `transform.deformations.twist_geometry` |
| 10 | Bend Geometry | `transform.deformations.bend_geometry` |

## Parameter rules

| Parameter | Rule |
|-----------|------|
| Twist / Bend / Taper Length | `> 0` |
| Spherical Radius / Falloff Power | `> 0` |
| Noise Frequency | `>= 0` (0 = constant field) |
| Noise Amplitude | any finite (negative OK) |
| Taper Start/End Scale | `>= 0` (0 = tip OK); no hidden minScale |
| Path Attract Strength | blend in `[0,1]` after valid resolve |
| Path Attract Radius | `> 0` |
| Relax K | exact Integer in `[1, N-1]` |
| Relax Iterations | exact Integer in `[1, 64]` |
| Relax Blend | finite in `[0,1]` |
| Lattice Min/Max | per-axis `min < max` (no swap) |
| Lattice Grid | property invariant `1..8` (setter only) |

## Spherical Displace

Outside radius → weight `0` (points unchanged). No `Affect Outside Radius` property.

## Path Attract tangent

`TOWARD_POINT` does not require a usable tangent. `TANGENTIAL` / `PERPENDICULAR` with
degenerate closest-point tangent → `Valid=false` (no world-+X fallback).

## Lattice outside-box samples

Points outside the lattice AABB use UVW clamped to `[0,1]` (boundary FFD). Documented, not a silent repair of domain validity.

## Twist / Bend Geometry source

```text
Geometry connected → Geometry only (null/invalid → fail; never read SDF)
Geometry unconnected → SDF port (connection-aware)
```

Non-SDF `GEOMETRY` is voxelized (`Approximate=true`). Voxel count over
`GenerationLimits.MAX_DEFORM_SOURCE_VOXELS` → fail closed.

## Budgets

- `GenerationLimits.MAX_RELAX_POINTS` (8192)
- `GenerationLimits.MAX_DEFORM_SOURCE_VOXELS` (32768)

No user-facing Max Points / Max Source Voxels properties.

## Shared helpers

- `PointUtils.resolveStrictPointList`
- `VectorUtils.resolveStrictVectorList` / `toVector` / `toVectorPort`
- `OptionalPortDrive`
- `StrictIntegerUtils`
- `DeformationUtils.rotateAroundAxis` only (no duplicate resolvers)

## Migration (V52 → V53)

Strip obsolete state keys: `minScale`, `maxPoints`, `maxSourceVoxels`, `affectOutsideRadius`.
No type-id remaps.
