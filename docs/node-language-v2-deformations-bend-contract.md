# Node Language v2 — Deformations Bend Contract (Graph V101)

**Status: ACTIVE** (Graph **V101**)

Closes Bend SDF P1 drift vs Bend Point List / Twist SDF: parallel bend-normal
fail-closed, output bounds from `SdfSource.min`/`max` with real `boundsSamples`,
and strict `BentSdfData` / `TwistedSdfData` constructors (no silent repair).

Related: [`node-language-v1-deformations.md`](./node-language-v1-deformations.md)
(V53 inventory; orders 9–10 superseded by V78 SDF ids).

## Contracts

### Parallel bend normal

`DeformationUtils.resolveBendFrame(axis, bendNormal)` projects the normal onto the
plane ⊥ axis. Returns `null` when axis/normal are unusable or **parallel** — no
`defaultNormal` inside the helper.

| Node | Behavior |
|------|----------|
| Bend SDF | Always fail-closed: `Valid=false`, error mentions parallel |
| Bend Point List CUSTOM | Same fail-closed |
| Bend Point List AUTO / XY / XZ / YZ | Still may fall back to `defaultNormal` when the chosen plane normal is parallel (AUTO policy unchanged) |

### Bend SDF bounds

Mirror Twist SDF:

1. `resolveSdfSource(boundsPadding)` → authoritative source AABB
2. `estimateBentBounds(source.min, source.max, bent, clampBoundsSamples(boundsSamples), boundsPadding)`
3. Sample a grid over the **source** box, map each sample through `bent.bendPoint`, union, expand by padding

Do **not** call `SdfBoundsEstimator.estimate(bent)` for the node Valid path. Explicit Bounds Min/Max on a custom (anonymous) SDF must succeed when the paired bounds are valid.

### Strict SDF datatypes

`BentSdfData` / `TwistedSdfData` constructors throw `IllegalArgumentException` on:

- null / non-finite / zero axis
- bend length / twist length ≤ 0 or non-finite (no `abs` / `max(EPS, …)` wash)
- Bent: bend normal parallel to axis after projection (no silent `defaultNormal`)

Null `clampMode` → `CLAMP`.

## Migration

V100 → V101 is a no-op format bump (runtime semantics only; no wire / state remaps).
