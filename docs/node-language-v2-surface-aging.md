# Node Language v2 — Surface Aging

**Status: PASSED / FROZEN** (Graph **V119**; V39 remains historical v1)

Strict sources & spatial sampling remediation for `material.surface_aging.*`: shared
`MaterialSourceResolver`, long-safe `MaterialSpatialUtils.Relative`, connection-aware
Amount / Aging Origin / BLOCK_TYPE, and duplicate-position rejection.

Related: [`node-language-v1-surface-aging.md`](./node-language-v1-surface-aging.md),
[`node-language-v2-pattern-mapping.md`](./node-language-v2-pattern-mapping.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Source precedence

Flat ports only (no tree ids):

```text
Placements → Coordinates → Geometry (GEOMETRY → BOX → CYLINDER → SPHERE → TORUS)
```

| State | Behavior |
|-------|----------|
| Not driven | skip to next precedence |
| Driven + valid | use; stop |
| Driven + invalid | `Valid=false`; **no** geometry/coords fallthrough |
| Driven + empty list | valid empty (no fallthrough) |

Shared with Basic Assignment / Directional / Gradient / Pattern:
`com.nodecraft.nodesystem.util.MaterialSourceResolver`.

## Relative coordinates

`MaterialSpatialUtils.Relative` uses `long dx/dy/dz`:

```text
dx = (long) pos.x - origin.x
```

Noise sampling casts to `double` **after** long subtraction:

```text
agingSample = (RandomOps.valueNoise3((double)dx, (double)dy, (double)dz, seed) + 1) * 0.5
```

## Aging Origin

| Port state | Behavior |
|------------|----------|
| Undriven | `(0,0,0)` |
| Driven + `BlockPos` | use it |
| Driven + null / wrong type | `Valid=false` |

## Amount

| Port state | Behavior |
|------------|----------|
| Undriven | node default (Weathering `0.2`, Moss `1.0`, Crack `0.15`) |
| Driven + finite `Double` in `[0,1]` | use it |
| Driven + null / wrong type / non-finite | `Valid=false` (no silent default) |

## BLOCK_TYPE roles

Base / Aged / Moss / Crack ports use `requireKnownBlockType`:

| Port state | Behavior |
|------------|----------|
| Undriven | absent (preserve source blockId) |
| Driven + known registry id | use it |
| Driven + blank / unknown | `Valid=false` |

Geometry / coordinates still require a driven Base Block when those sources are used.

## Duplicate positions

Two or more placements at the same `BlockPos` → `Valid=false` (Surface Aging group
contract until a global placement dedup policy exists).

## Affected Count

Counts voxels where aging mapping **ran**: eligibility passed, random mask selected
aging, and the aging role block was set. Does **not** require the output `blockId` to
differ from the source.

## Core rules (unchanged from v1)

1. **PURE** — input-set topology only.
2. **blockId only** — `pos` and `stateData` preserved.
3. **Weathering / Crack** — any missing 6-neighbor is surface-eligible.
4. **Moss Growth** — top-exposed only (`up` missing).
5. **Amount boundaries** — `0` → none; `1` → all eligible (via `shouldAge`).
6. **Seed** — `RandomOps.resolveSeed` (Integer-only).

## Migration

Graph **V118→V119** is a no-op (Valid semantics; no wire remaps).

## Contract

- `SurfaceAgingLanguageV2ContractTest` — V119 fence, mixed placements, no
  connected-invalid fallback, duplicate positions, extreme relative coords, driven
  Amount/Origin/BLOCK_TYPE fail-closed, topology smoke.
- `SurfaceAgingLanguageContractTest` — V39 inventory retained.
