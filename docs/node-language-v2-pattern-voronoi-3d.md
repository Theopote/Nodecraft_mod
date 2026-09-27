# Node Language v2 — Pattern Voronoi 3D

**Status: implemented** (Graph **V83**; V45 remains historical v1)

Language modernization for the single canonical `pattern.voronoi_3d.lloyd_relax`
node: Valid+Error, OptionalPortDrive for Cells/Iterations, order 0, and a
transactional publish fence. Grid-approximated Lloyd algorithm unchanged.

Related: [`node-language-v1-pattern-voronoi-3d.md`](./node-language-v1-pattern-voronoi-3d.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Corner A / Corner B** — `POINT` ports; component-wise min/max normalization is order-independent.
2. **OptionalPortDrive Integer** — Cells / Iterations: unconnected → property; connected exact Integer → wire; connected non-exact → `Valid=false` (no property fallback).
3. **Range fail-closed** — Cells ∈ [4, 96]; Iterations ∈ [0, 32] (no clamp).
4. **Finite validation** — corners and sites must be finite.
5. **Non-degenerate 3D bounds** — span on each axis must exceed epsilon.
6. **Sites >= 1**, all inside inclusive AABB, all distinct (distance² ≤ 1e-12 → invalid).
7. **Iterations = 0** — deep-copy passthrough; util not called.
8. **Publish fence** — success only when relaxed cardinality matches input, every site finite and inside bounds; otherwise whole fail (`Count=0`, empty Sites, Error set).
9. **Fail closed on budget** — site cap / estimated Lloyd work over limit → invalid.
10. **Deterministic** — same inputs → same outputs (no RNG).

## Inventory (order 0)

| Order | Display name | Type id |
|------:|--------------|---------|
| 0 | Lloyd Relax 3D | `pattern.voronoi_3d.lloyd_relax` |

## Outputs

| Port | Type | Role |
|------|------|------|
| Sites | POINT_LIST | Relaxed site positions |
| Count | INTEGER | Number of sites |
| Valid | BOOLEAN | True when relaxation succeeded |
| Error | STRING | Failure reason when Valid is false |

## Migration (V82 → V83)

Format bump only. Error is additive; order is catalog metadata. No wire remaps/drops.
