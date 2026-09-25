# Node Language v1 — Pattern Mapping

**Status: PASSED / FROZEN** (Graph **V38**)

Language unification for exactly **4** `material.pattern_mapping.*` nodes: PURE
role-based (Primary/Secondary or Frame/Fill) pattern remapping of `blockId` only,
with explicit **Pattern Origin**, fail-closed integer dimensions, and no hidden
vanilla material defaults.

Shared helpers: `PatternMaterialUtils` + `MaterialMappingSupport` +
`BrickPatternMapping`. Graph schema: **V38** drops `output_positions` /
`output_block_ids` wires from the four pattern nodes.

Related: [`node-language-v1-gradient-mapping.md`](./node-language-v1-gradient-mapping.md),
[`node-language-v1-directional-mapping.md`](./node-language-v1-directional-mapping.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **PURE** — all four nodes.
2. **blockId only** — `pos` and `stateData` unchanged.
3. **Role-based materials** — two `BLOCK_TYPE` ports (not `BLOCK_PALETTE`).
4. **Preserve on partial override** — placement source + unconnected role for that
   cell → keep `source.blockId` (never invent stone/andesite/bricks/quartz).
5. **Geometry / coordinates** — at least one explicit role material required;
   otherwise `Valid=false`, `[]`.
6. **No deconstruct outputs** — `BLOCK_PLACEMENT_LIST` + `Valid` / `Error` only.
7. **Fail-closed integers** — Stripe Width / Brick Length / Course Height / Grid Size
   `>= 1`; Line Width satisfies `1 ≤ width ≤ Grid Size`. No silent clamps.
8. **Pattern Origin** — `input_pattern_origin : BLOCK_POS`:
   - missing (`null`) → `(0,0,0)`, `Valid=true` (preserves pre-V38 world-anchored look)
   - `BlockPos` → use it
   - any other runtime type (`POINT`, `VECTOR`, …) → `Valid=false`
   All pattern math uses relative `(dx,dy,dz)`.

## Inventory (4)

| Display name | Type id | Pattern |
|--------------|---------|---------|
| Checker Pattern Map | `material.pattern_mapping.checker_pattern_map` | 3D parity `(dx+dy+dz) & 1` |
| Stripe Pattern Map | `material.pattern_mapping.stripe_pattern_map` | axis `floorDiv` stripes |
| Brick Pattern Map | `material.pattern_mapping.brick_pattern_map` | running-bond X/Z |
| Grid Pattern Map | `material.pattern_mapping.grid_pattern_map` | X/Z grid (Y extruded) |

## Checker Pattern Map

**3D checker:** Y flips phase each layer. Primary on even parity, Secondary on odd.

## Stripe Pattern Map

Properties: Axis `X/Y/Z`, Stripe Width `>= 1`. Uses `Math.floorDiv` (negative-safe).

## Brick Pattern Map

Properties: Brick Length / Course Height `>= 1`; Brick Direction `Auto` / `X` / `Z`
(default Auto → span-based `resolveAxis`).

## Grid Pattern Map

**v1 = X/Z grid only** (Y extruded). Properties: Grid Size `>= 1`;
`1 ≤ Line Width ≤ Grid Size`. Uses `Math.floorMod`.

## Graph migration (V37→V38)

| Action | Detail |
|--------|--------|
| Drop wires | `output_positions`, `output_block_ids` from all four pattern_mapping nodes |
| Origin | New optional `input_pattern_origin`; missing → runtime `(0,0,0)` |

## Contracts

- `PatternMappingLanguageContractTest` — inventory, PURE, preserve/no-stone, fail-closed
  dimensions, Origin phase, 3D checker / XZ grid, Brick Direction, V37→V38 migration.
- `BrickPatternMappingTest` — axis resolve, relative index, no silent clamp.
- Format fences bumped to **V38**.
