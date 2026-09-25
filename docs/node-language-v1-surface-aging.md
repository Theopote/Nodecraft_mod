# Node Language v1 — Surface Aging

**Status: PASSED / FROZEN** (Graph **V39**)

Language unification for exactly **3** `material.surface_aging.*` nodes: PURE
topology-based aging that remaps `blockId` only on surface-eligible voxels,
driven by `RandomOps.valueNoise3` + Amount, with explicit **Aging Origin**,
fail-closed Amount `[0,1]`, and no hidden vanilla material defaults.

Shared helpers: `SurfaceAgingUtils` + `MaterialMappingSupport`. Graph schema:
**V39** drops `output_positions` / `output_block_ids` wires from all three nodes,
and drops Crack legacy `input_interval` wires.

Related: [`node-language-v1-pattern-mapping.md`](./node-language-v1-pattern-mapping.md),
[`node-language-v1-directional-mapping.md`](./node-language-v1-directional-mapping.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **PURE** — all three nodes (input-set topology only; never `world.isAir`).
2. **blockId only** — `pos` and `stateData` unchanged.
3. **Exposure from occupancy** — Weathering / Surface Cracks: any missing 6-neighbor;
   Moss Growth: **top-exposed only** (`up` missing).
4. **Mask** — `agingSample = (RandomOps.valueNoise3(dx,dy,dz,seed) + 1) * 0.5`;
   age when `sample < Amount`. Seed via `RandomOps.resolveSeed` (Integer-only).
5. **Amount** — finite and exact `[0, 1]`; no `clamp01`, no NaN success.
6. **Preserve on partial override** — placement source + unconnected aged role → keep
   `source.blockId` (never invent stone_bricks / moss defaults).
7. **Geometry / coordinates** — **Base Block required**. Aging role (Aged/Moss/Crack)
   is optional (missing → preserve base; Affected Count = 0). Placements source does
   not require Base (source already has `blockId`). Aging-role-only geometry →
   `Valid=false` (never voxelize the whole model as the aging material).
8. **No deconstruct outputs** — `BLOCK_PLACEMENT_LIST` + `Valid` / `Error` +
   `Affected Count`.
9. **Aging Origin** — `input_aging_origin : BLOCK_POS`:
   - missing (`null`) → `(0,0,0)`, `Valid=true`
   - `BlockPos` → use it
   - any other runtime type (`POINT`, `VECTOR`, …) → `Valid=false`
   All noise uses relative `(dx,dy,dz)`.

## Inventory (3)

| Display name | Type id | Eligibility |
|--------------|---------|-------------|
| Weathering | `material.surface_aging.weathering` | any 6-neighbor surface |
| Moss Growth | `material.surface_aging.moss_growth` | top-exposed only |
| Surface Cracks | `material.surface_aging.crack_pattern` | any 6-neighbor surface |

## Weathering

Roles: Base Block (**required** for geometry/coords voxelization), Aged Block
(optional aged surface cells). Amount default `0.2`. Interior voxels in a solid
occupancy set are never aged.

## Moss Growth

Roles: Base Block (**required** for geometry/coords), Moss Block (optional).
Moss prefers upward-exposed voxels (v1 = top-only; no side bias weights).
Amount default `1.0`.

## Surface Cracks

Display rename only (type id unchanged). Roles: Base Block (**required** for
geometry/coords), Crack Block (optional). Replaces legacy Interval world-stripe
formula with Amount `[0,1]` + Seed RandomOps sparse mask on surface voxels.

## Graph migration (V38→V39)

| Action | Detail |
|--------|--------|
| Drop wires | `output_positions`, `output_block_ids` from all three surface_aging nodes |
| Crack | Drop wires targeting removed `input_interval` |
| Origin / Amount / Seed | Missing → runtime defaults (origin `(0,0,0)`) |

## Contracts

- `SurfaceAgingLanguageContractTest` — inventory, PURE, preserve/no-stone, surface vs
  interior, moss top-only, Amount NaN, Seed Integer-only, Origin fail-closed,
  geometry Base required (aging-role-only invalid), V38→V39 migration.
- Format fences bumped to **V39**.
