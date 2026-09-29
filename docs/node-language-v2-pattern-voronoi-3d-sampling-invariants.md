# Node Language v2 — Pattern Voronoi 3D Sampling Invariants (Graph V107)

**Status: ACTIVE** (Graph **V107**)

Closes Pattern Voronoi 3D P1/P2 after V83 language freeze:

1. Grid sampling capacity invariant when `Iterations > 0`
2. Output distinctness fence in publish path
3. Bounded Sites preflight before list materialization
4. Documented empty-cell retain policy

Related: [`node-language-v2-pattern-voronoi-3d.md`](./node-language-v2-pattern-voronoi-3d.md) (V83 foundation),
[`node-language-v1-pattern-voronoi-3d.md`](./node-language-v1-pattern-voronoi-3d.md).

## Grid sampling capacity

The Lloyd util uses a uniform `cells × cells × cells` sampling grid. Each grid cell votes for its nearest site; sites move to the centroid of owned cells.

| Iterations | Rule |
|------------|------|
| `0` | No `sites <= cells³` requirement (exact passthrough unchanged) |
| `> 0` | `siteCount <= cellsPerAxis³` required |

**Fail-closed:** when `Iterations > 0` and site count exceeds grid samples, the node rejects before calling the util (prevents silent under-sampling where most sites never relax).

Example: `cells=4` → max 64 active grid owners; 4096 sites with `Iterations=1` must fail, not silently pass.

## Output distinctness

Input sites must be distinct (distance² ≤ 1e-12 → invalid). After relaxation, `commitRelaxed` re-checks output sites with the same epsilon. Duplicate or near-duplicate relaxed positions fail closed.

## Bounded Sites preflight

Raw `Sites` list size is checked against `MAX_VORONOI_LLOYD_SITES` (4096) before allocating the resolved point list. Mirrors bounded preflight patterns in other Pattern nodes.

## Empty-cell policy

Sites owning **zero** grid samples retain their **previous position** for that Lloyd round. This is inherent to grid-approximated Lloyd (`Voronoi3DGridLloyd`); V107 documents it explicitly.

## Migration

V106 → V107 is a no-op format bump (runtime semantics only). No wire remaps or drops.
