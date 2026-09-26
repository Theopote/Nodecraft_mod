# Node Language v1 — Surface / Volume Distribution

**Status: PASSED / FROZEN** (Graph **V44**)

Language unification for the six canonical `pattern.surface_volume_distribution.*`
nodes: continuous spatial sampling producers aligned with Pattern Linear/Grid/Radial
v1 rules — no BLOCK_LIST mirrors, deterministic seeds, strict Integer ports.

Related: [`node-language-v1-pattern-radial.md`](./node-language-v1-pattern-radial.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Sample nodes (structured)** — Count = exact emitted samples when Count > 0.
2. **Target-count scatter nodes** — Target Count is a maximum; output Count = actual accepted (<= target).
3. **Target Count <= 0** -> empty output and Valid=false.
4. **INTEGER ports accept Integer only** — no silent Number truncation (e.g. 3.8 -> 3).
5. **DOUBLE inputs must be finite** — NaN/Infinity fail closed.
6. **Seed is deterministic** — property default 12345; port override when connected; never wall-clock seeds.
7. **No BLOCK_LIST outputs** — block quantization belongs downstream.
8. **Min Distance is strict** — spacing constraints are never relaxed to hit Target Count.
9. **Layout outputs capped** via `GenerationLimits.clampLayoutInstanceCount()` (16,384).

## Inventory (6)

| Display name | Type id | Role |
|--------------|---------|------|
| Sample Sphere Surface | `pattern.surface_volume_distribution.sample_sphere_surface` | Structured sphere surface sampler |
| Scatter On Surface | `pattern.surface_volume_distribution.scatter_surface` | Primitive geometry surface scatter |
| Poisson Disk On Plane | `pattern.surface_volume_distribution.poisson_disk_plane` | Planar minimum-distance scatter |
| Scatter On Surface Strip | `pattern.surface_volume_distribution.scatter_surface_strip` | SurfaceStrip layout scatter |
| Scatter In Volume | `pattern.surface_volume_distribution.scatter_volume` | Primitive volume interior scatter |
| Image Scatter | `pattern.surface_volume_distribution.image_scatter` | Density-map planar scatter |

## Node categories

### A. Structured sample — Sample Sphere Surface

```
Sphere -> Sample Sphere Surface -> Points + Normals
```

Modes: Fibonacci, Random, Lat/Long. Count = exact sample count.

### B. Target-count scatter — Poisson, Surface, Volume, Strip, Image

Shared semantics:

- **Target Count** port (`input_target_count`) — maximum requested samples
- **Count** output — actual accepted samples (may be less than target)
- **Valid=true** when inputs are valid (under-target is not invalid)

Scatter On Surface / Scatter In Volume use **primitive continuous sampling**
(Sphere, Box, Cylinder, Torus, Cone, Ellipsoid, Hemisphere). Composite/Boolean
geometry fails closed (Valid=false) — no silent voxelize.

Scatter On Surface Strip validates uniform section topology, respects
`sectionClosedFlags` (open sections do not wrap last-to-first), and uses globally
area-weighted dual-triangle quad sampling.

Image Scatter inputs:

- `Density Values` (DOUBLE_LIST) + `Width`/`Height` (INTEGER), or `Image Path`
- Outputs aligned `U Values`, `V Values`, `Density Values` (DOUBLE_LIST)

### C. Downstream

```
Sample / Scatter -> Instance on Points -> Voxelize / Block placement
Scatter On Surface -> Place Geometry on Frames (via normals/frames in P2)
```

## Removed legacy

- `populate_region` (BLOCK_LIST era)
- `surface_scatter` (duplicate sphere scatter + block snap)
- `sample_geometry_surface` / `scatter_geometry_surface` (voxel disguised as continuous)
- `sample_surface` (renamed to `sample_sphere_surface`)
- BLOCK_LIST mirror outputs on all scatter nodes
- RELAXED spacing fallback
- Multi-geometry fallback ports (Box/Cylinder/Sphere/Torus separate inputs)
- Image Scatter bare LIST UV output and mixed grayscale LIST input

## Graph format

Surface / Volume Distribution v1 ships at **Graph V44**. No dedicated V43->V44 migration.

## Contracts

- `PatternSurfaceVolumeDistributionLanguageContractTest` — inventory, Count/seed/finite
  rules, no BLOCK_LIST, primitive continuous sampling, strip topology, spacing strictness.

## Deferred (P2)

- Generic mesh/triangle surface and volume sampling
- Surface Strip Normals + Frames outputs
- `Complete : BOOLEAN` output for under-target scatter
- Polar-style Angles output (N/A here)
