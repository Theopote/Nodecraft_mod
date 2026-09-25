# Node Language v1 — Input Context

**Status: PASSED / FROZEN** (post-implementation working tree, Graph **V32**)

Language unification for `input.context.*` (4 nodes): one fail-closed runtime contract for
world/player reads. Missing context must never look like Overworld, origin, morning, or a
raycast miss when context is unavailable.

Shared helper: `ContextReadUtils`. Graph schema: **V32** renames Player Look At → Player Raycast,
retargets hit position to `POINT`, distance and time ticks to `DOUBLE`, adds `output_valid` on
all four nodes, and drops incompatible downstream wires.

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`node-language-v1-input-numeric.md`](./node-language-v1-input-numeric.md),
[`type-conversion-guidelines.md`](./type-conversion-guidelines.md).

## Core rules

1. **`output_valid` gate** — `true` only when the node had live world/player context (or, for
   Player Position Snapshot, a captured snapshot). Downstream must treat `Valid=false` as “no
   trustworthy world read,” not as a neutral default value.
2. **No fake world state** — invalid context must not emit Overworld id, `(0,0,0)` as if real,
   fake 06:00 / `Is Day=true`, or raycast miss semantics when context is missing.
3. **Raycast miss ≠ invalid** — when context is live but nothing is hit: `Valid=true`,
   `Has Hit=false`, neutral hit outputs.
4. **Spatial typing** — hit location is `POINT` (`PointData`), not `VECTOR`. Distance is
   `DOUBLE`.
5. **Time ticks** — `output_time_ticks` is `DOUBLE` (world tick count represented as `DOUBLE`);
   day decomposition
   uses `Math.floorMod(worldTimeTicks, 24000L)`.

## Inventory (4)

| Display name | Type id | Class | Effect |
|--------------|---------|-------|--------|
| Player Position Snapshot | `input.context.player_position` | `PlayerPositionNode` | WORLD_READ |
| Player Raycast | `input.context.player_raycast` | `PlayerRaycastNode` | WORLD_READ |
| Dimension Info | `input.context.dimension_info` | `DimensionInfoNode` | WORLD_READ |
| Current Time | `input.context.current_time` | `CurrentTimeNode` | WORLD_READ |

Legacy type id `input.context.player_look_direction` remaps to `input.context.player_raycast` on load (V32).

## Player Position Snapshot (`input.context.player_position`)

Captures a **continuous** player world position snapshot (first capture on run; **Update Position**
recaptures). Display copy no longer says “snapped.”

| Output | Type | When `Valid=false` |
|--------|------|---------------------|
| `output_valid` | `BOOLEAN` | No snapshot yet / restore without snapshot / non-finite coords |
| `output_position` | `POINT` | `null` |
| `output_x` / `output_y` / `output_z` | `DOUBLE` | `0.0` (neutral; gated by Valid) |

Persisted snapshot restore rejects non-finite `cachedX/Y/Z` (`setNodeState` sanitize + defensive
`setCachedPosition` guard). `Valid=true` never pairs with NaN/Infinity coordinates.

Server capture reads `ServerPlayerEntity` doubles directly (bypasses float `PlayerAccessor` path).

## Player Raycast (`input.context.player_raycast`)

Renamed from **Player Look At**. Raycasts from player eye + view vector.

| Output | Type | Notes |
|--------|------|-------|
| `output_valid` | `BOOLEAN` | `false` when context unavailable |
| `output_hit_position` | `POINT` | `null` on miss or invalid |
| `output_hit_distance` | `DOUBLE` | `0.0` on miss or invalid |
| `output_has_hit` | `BOOLEAN` | `false` on miss **or** invalid |
| `output_hit_block` | `BLOCK_INFO` | `null` when no hit |
| `output_hit_entity` | `ENTITY_INFO` | `null` when no hit |

| Semantics | `Valid` | `Has Hit` |
|-----------|---------|-----------|
| Invalid context | `false` | `false` |
| Live miss | `true` | `false` |
| Live hit | `true` | `true` |

**Max Distance** property: `double`, finite, clamp `[0, 1000]`. Raycast uses `player.getEyePos()`
and `getRotationVec(1.0f)` (double path, not float accessor `Vector3`).

## Dimension Info (`input.context.dimension_info`)

| Output | Type | Invalid context |
|--------|------|-----------------|
| `output_valid` | `BOOLEAN` | `false` |
| `output_dimension_id` | `STRING` | `""` (not Overworld default) |
| `output_is_overworld` / `nether` / `end` | `BOOLEAN` | all `false` |
| `output_has_skylight` / `output_has_ceiling` | `BOOLEAN` | `false` |

When valid: dimension id from accessor; skylight/ceiling from `world.getDimension()`
(`DimensionType.hasSkyLight()` / `hasCeiling()`). Overworld/Nether/End flags remain id-based convenience.

## Current Time (`input.context.current_time`)

| Output | Type | Invalid context |
|--------|------|-----------------|
| `output_valid` | `BOOLEAN` | `false` |
| `output_time_ticks` | `DOUBLE` | `0.0` |
| `output_day` / `output_day_time` / `output_hour` / `output_minute` | `INTEGER` | `0` |
| `output_is_day` / `output_is_night` / rain / thunder | `BOOLEAN` | all `false` (no fake morning) |

Runtime object on `output_time_ticks` is `Double`, not `Long` or `Integer`.

## Shared helpers (`ContextReadUtils`)

| Helper | Role |
|--------|------|
| `isLiveContextAvailable` | `context`, world, player, and player accessor all non-null |
| `sanitizeMaxDistance` | Finite max distance for raycast property |

## Infrastructure debt (out of scope V32)

- `PlayerAccessor` still exposes float `Vector3` for position/look; context nodes bypass it
  where double precision matters. Full accessor → `PointData` / `Vector3d` migration deferred.

## Graph migration (V31→V32)

| Action | Detail |
|--------|--------|
| Node type remap | `input.context.player_look_direction` → `input.context.player_raycast` |
| Hit position wires | Drop where declared `POINT` source is incompatible (legacy `VECTOR` hit port) |
| Hit distance wires | Drop where `FLOAT→DOUBLE` breaks declared connectability |
| Time ticks wires | Drop where `INTEGER→DOUBLE` breaks declared connectability |
| New ports | `output_valid` on all four — no wire migration |

## Contracts

- `InputContextLanguageContractTest` — 4-node inventory, WORLD_READ, port types, fail-closed
  `processNode(null)`, snapshot restore (missing / non-finite persisted coords), V31→V32 migration.
- Format contract tests bumped to **V32** (`GraphFormatVersionContractTest`, family fences).
