# Node Language v1 — Pattern Voronoi 3D

**Status: PASSED / FROZEN** (Graph **V45**)

Language unification for the single canonical `pattern.voronoi_3d.*` node: grid-approximated
Lloyd relaxation inside an axis-aligned 3D box — aligned with Pattern Linear/Grid/Radial/Surface
v1 typed spatial ports, strict Integer semantics, and fail-closed validation.

Related: [`node-language-v1-pattern-radial.md`](./node-language-v1-pattern-radial.md),
[`node-language-v1-pattern-surface-volume-distribution.md`](./node-language-v1-pattern-surface-volume-distribution.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Corner A / Corner B are positions** — `POINT` ports resolved via `SpatialValueResolver.resolvePoint()`.
   Component-wise min/max normalization is order-independent.
2. **Strict Integer** — `Cells` and `Iterations` accept `Integer` only; non-Integer wired values
   use node property fallback (default 24 / 4).
3. **Finite validation** — all corners and all sites must be finite; otherwise `Valid=false`, empty output.
4. **Non-degenerate 3D bounds** — after normalization, span on each axis must exceed epsilon;
   zero-thickness boxes are invalid (no implicit 2D/1D fallback).
5. **Sites >= 1**, all **inside bounds** (inclusive), all **distinct** (distance <= 1e-6 -> invalid).
6. **Cells in [4, 96]** — `Cells < 4` is invalid (no silent bump to 4).
7. **Iterations in [0, 32]** — `Iterations < 0` is invalid; **`Iterations = 0` is exact passthrough**
   (same positions, same count, `Valid=true`; util is not called).
8. **Index identity preserved** — output site count equals input count; site `i` maps to site `i`.
9. **Deterministic** — same inputs produce same outputs (no RNG).
10. **Fail closed on budget** — reject when site count or estimated work exceeds caps.

## Inventory (1)

| Display name | Type id | Role |
|--------------|---------|------|
| Lloyd Relax 3D | `pattern.voronoi_3d.lloyd_relax` | Grid-approximated Lloyd relaxation in axis-aligned 3D box |

## Algorithm scope

Uniform `n x n x n` sampling grid inside the axis-aligned box. Each cell center votes for its
nearest site; sites move to the centroid of owned cells; repeat for `Iterations` rounds.

This is **not** an exact 3D Voronoi diagram. The node stays small and honest about approximation.

## Port semantics

### Inputs

| Port | Type | Role |
|------|------|------|
| Sites | POINT_LIST | Seed site positions (>= 1) |
| Corner A | POINT | First corner of bounds box |
| Corner B | POINT | Second corner of bounds box |
| Cells | INTEGER | Grid cells per axis (optional override; default 24) |
| Iterations | INTEGER | Lloyd rounds (optional override; default 4) |

### Outputs

| Port | Type | Role |
|------|------|------|
| Sites | POINT_LIST | Relaxed site positions |
| Count | INTEGER | Number of sites |
| Valid | BOOLEAN | True when relaxation succeeded |

Legacy ports `input_min` / `input_max` (VECTOR) are removed at Graph V45.

## Validation summary

- Missing/empty sites or corners -> invalid
- Non-finite corner or site -> invalid
- Degenerate bounds on any axis -> invalid
- Site outside normalized bounds -> invalid
- Duplicate/near-duplicate sites -> invalid
- Cells outside [4, 96] -> invalid
- Iterations outside [0, 32] -> invalid
- Site count > `MAX_VORONOI_LLOYD_SITES` (4096) -> invalid
- Estimated work `cells^3 * siteCount * iterations` > `MAX_LLOYD_DISTANCE_TESTS` (100M) -> invalid

## Iterations = 0 passthrough

When `Iterations = 0`, the node emits a deep copy of input sites without calling the Lloyd util.
`Valid=true`, `Count = input site count`, positions unchanged.

## Deferred (P2)

- Exact 3D Voronoi polyhedra outputs
- 3D Delaunay triangulation
- Cell adjacency / neighbor lists
- Additional nodes in `pattern.voronoi_3d`
