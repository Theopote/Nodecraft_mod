# Node Language v1 — Pattern Radial

**Status: PASSED / FROZEN** (Graph **V43**)

Language unification for the three canonical `pattern.radial.*` nodes: one geometry-first
polar array plus two continuous radial layout producers — aligned with Pattern Linear/Grid
v1 Count semantics, typed spatial ports, and geometry-first workflows.

Related: [`node-language-v1-pattern-linear.md`](./node-language-v1-pattern-linear.md),
[`node-language-v1-pattern-grid.md`](./node-language-v1-pattern-grid.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Count = total emitted instances** — array and layout nodes emit exactly Count anchors/copies.
2. **Count <= 0 is empty** — empty output and Valid=false.
3. **INTEGER ports accept Integer only** — no silent Number truncation (e.g. 3.8 -> 3).
4. **Layout producers emit POINT_LIST + VECTOR_LIST + FRAME_LIST** — no BLOCK_LIST copying.
5. **Polar Array is the sole geometry-copy node** — Spiral/Phyllotaxis are layout producers.
6. **Geometry output shape (Polar Array)** — 0 -> null, 1 -> raw Geometry, 2+ -> CompositeGeometryData.
7. **Spatial DOUBLE inputs must be finite** — NaN/Infinity fail closed on layout producers.
8. **Negative Turns / Radius Step / Height Step are allowed** on Spiral when finite.

## Inventory (3)

| Display name | Type id | Role |
|--------------|---------|------|
| Polar Array | `pattern.radial.polar_array` | Geometry copies rotated around center/axis |
| Spiral | `pattern.radial.spiral` | Spiral anchor layout + tangents + frames |
| Phyllotaxis | `pattern.radial.phyllotaxis` | Golden-angle distribution + tangents + frames |

## Node categories

### A. Geometry Array — Polar Array

```
Geometry -> Polar Array -> Voxelize Geometry -> Assign Block Type
```

Inputs: Geometry, Center (POINT), Axis (VECTOR), Count, Total Angle (degrees).

Outputs: Geometry, Geometries, Geometry Tree, Count, Valid.

**Include End** (node property): when true and Count >= 2 on a partial arc, the last
instance sits at Total Angle. Count always means total instances; Include End only changes
sampling domain. Ignored when |Total Angle| is a nonzero multiple of 360° (exclusive-end
sampling avoids seam duplication).

**Full circle**: 360°, 720°, -360°, etc. use exclusive-end sampling (0°, 90°, 180°, 270°
for Count=4 / 360° — not 360° duplicate).

**Total Angle = 0, Count > 1**: legal — coincident rotated copies at the same angle.

Geometry is truly rotated around the axis (CAD array behavior), not just repositioned.

### B. Radial layout producers — Spiral and Phyllotaxis

Shared outputs:

- `output_points` (POINT_LIST)
- `output_tangents` (VECTOR_LIST)
- `output_frames` (FRAME_LIST)
- `output_count`, `output_valid`

Frames use shared `RadialFrameUtils` -> `PathFrameUtils` placement convention.

Downstream:

```
Spiral / Phyllotaxis -> Instance on Points
Spiral / Phyllotaxis -> Place Geometry on Frames
```

**Spiral** inputs: Origin, Turns, Count, Start Radius, Radius Step, Height Step, Start Angle.

**Phyllotaxis** inputs: Origin, Count, Radius Scale, Angle Step (default 137.507764°),
Start Angle, Height Step, Radial Exponent (DOUBLE port, default 0.5).

No Align To Tangent property — choose Points or Frames downstream instead.

## Removed legacy

- BLOCK_LIST `pattern.radial.polar_array` (old coordinate rotator)
- `pattern.radial.polar_array_geometry` (merged into canonical Polar Array)
- `pattern.radial.spiral_array` (renamed to `pattern.radial.spiral`)
- BLOCK_LIST template copying on Spiral/Phyllotaxis
- Align To Tangent node properties on Spiral/Phyllotaxis
- Radial Exponent as Phyllotaxis node property (now DOUBLE port)

## Graph format

Pattern Radial v1 ships at **Graph V43**. No dedicated V42->V43 migration — development
builds use current node ids and ports directly.

## Contracts

- `PatternRadialLanguageContractTest` — inventory, Polar full-circle/Include End, layout
  producer alignment, Count semantics, finite validation, output caps.
- `PatternArrayFamilyContractTest` — cross-pattern array/frame integration (Linear/Curve).

## Deferred (P2)

- Polar `Angles : DOUBLE_LIST` output
- Arbitrary-axis Spiral, 3D Phyllotaxis, radial jitter, Concentric Rings
