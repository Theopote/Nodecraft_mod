# Node Language v2 — Surface / Volume Uniformity & Topology (Graph V105)

**Status: ACTIVE** (Graph **V105**)

Closes Surface / Volume Distribution P1 sampling uniformity and strict Surface Strip
topology after V82 language freeze:

1. Cone lateral and Torus surface area-uniform RANDOM sampling
2. Surface Strip strict quad validation (fail-closed, no silent quad drop)
3. Poisson Disk Max Attempts fail-closed (no setter floor clamp)

Related: [`node-language-v2-pattern-surface-volume-distribution.md`](./node-language-v2-pattern-surface-volume-distribution.md) (V82 foundation; V105 runtime contracts).

## Area-uniform primitive surface sampling

`PrimitiveGeometrySurfaceSampler` RANDOM mode (Scatter On Surface) is area-uniform for:

| Primitive | Policy |
|-----------|--------|
| Sphere, Hemisphere | Uniform on sphere |
| Box | Face area-weighted |
| Cylinder | Lateral area-uniform |
| Cone | Lateral area-uniform (`t = sqrt(u)`, Graph V105) |
| Torus | Area-uniform via rejection on minor parameter (Graph V105) |
| Ellipsoid | Analytic continuous (**not** area-uniform; unchanged) |

## Surface Strip strict validation

`SurfaceStripSampling.validateStrict` delegates to `SurfaceStripValidator`, then requires
every cross-section quad to have finite corners and area > EPS.

Scatter On Surface Strip calls `validateStrict` before `QuadCatalog` sampling. Any invalid
quad fails the whole node (`Valid=false`) — no silent filtering of degenerate faces.

## Poisson Max Attempts

`PoissonDiskOnPlaneNode` stores raw `maxAttempts` in state. `processNode` fails when
`maxAttempts < 100`. Upper bound capping via `GenerationLimits.clampAttemptBudget` is unchanged.

## Migration

V104 → V105 is a no-op format bump (runtime semantics only).
