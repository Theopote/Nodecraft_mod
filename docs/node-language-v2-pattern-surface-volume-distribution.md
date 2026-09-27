# Node Language v2 — Surface / Volume Distribution

**Status: PASSED / FROZEN** (Graph **V82**; V44 remains historical v1)

Language modernization for the six canonical `pattern.surface_volume_distribution.*`
nodes aligned with Pattern Linear/Grid/Radial v2 rules: Valid+Error+Complete,
OptionalPortDrive, fail-closed Count budgets, continuous volume bounds.

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Exact-count samplers** — Sample Sphere Surface, Image Scatter: success ⇒ Count == requested and Complete=true.
2. **Target-count scatter** — Surface / Poisson / Strip / Volume: Target Count is a maximum; under-target ⇒ Valid=true, Complete=false.
3. **Count / Target Count <= 0 or > MAX_LAYOUT_INSTANCES** → Valid=false (never `clampLayoutInstanceCount`).
4. **INTEGER ports** — OptionalPortDrive exact Integer; connected non-Integer fails (no property fallback).
5. **DOUBLE inputs** — finite; Min Distance >= 0; Half U/V / Span > 0; Threshold ∈ [0,1] (no clamp).
6. **Seed** — deterministic; connection-aware.
7. **No BLOCK_LIST / raw LIST** outputs.
8. **Degenerate primitives** fail closed (no world-axis / point repair).
9. **Volume Torus** uses continuous AABB (`GeometryBoundsResolver`) — never GeometryVoxelizer / BlockPos.
10. **Image Scatter** — Density Values + Width + Height all required and connected; size exactly Width×Height; densities ∈ [0,1]; no FILE_PATH (use Read Image upstream).
11. **Min Distance > 0** — selection workload preflight (`candidate×target` or `24×target²` for blue-noise approx) against `MAX_SCATTER_DISTANCE_TESTS`; over budget → Valid=false.

## Inventory (orders 0–5)

| Order | Display name | Type id |
|------:|--------------|---------|
| 0 | Sample Sphere Surface | `...sample_sphere_surface` |
| 1 | Scatter On Surface | `...scatter_surface` |
| 2 | Poisson Disk On Plane | `...poisson_disk_plane` |
| 3 | Scatter On Surface Strip | `...scatter_surface_strip` |
| 4 | Scatter In Volume | `...scatter_volume` |
| 5 | Image Scatter | `...image_scatter` |

## Sampling notes

- Cylinder / Cone: **lateral surface only**.
- Ellipsoid: analytic continuous sampling (not area-uniform).
- BLUE_NOISE distribution mode is an **approximate** blue-noise selector (not Bridson Poisson-disk).

## Migration (V81 → V82)

Drops wires targeting `image_scatter.input_image_path` (including subgraphs). Error/Complete are additive.
