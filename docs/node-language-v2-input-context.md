# Node Language v2 — Input Context

**Status: PASSED / FROZEN** (Graph **V112**; V32 remains historical v1)

Snapshot-data modernization plus freeze hardening for Player View Raycast and Player Position
Snapshot. Current Time and Dimension Info unchanged from V32 fail-closed contracts.

Related: [`node-language-v1-input-context.md`](./node-language-v1-input-context.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Canonical snapshots

| Port type | Payload | Notes |
|-----------|---------|-------|
| `BLOCK_INFO` | `BlockInfoData` | Position, registry id, `BlockStateData` properties, `isAir` |
| `ENTITY_INFO` | `EntityInfoData` | UUID, type id, position, bbox min/max, optional display name |

**Never** put live `BlockState` or `Entity` on Raycast outputs. Hit-time snapshot only.

Legacy world.write wires may still carry `BlockState` / block-id `String` on `BLOCK_INFO`
(accepted by `isCompatible` and `WorldWriteUtils.resolveBlockState`). Raycast always emits
`BlockInfoData`.

## Player View Raycast (`input.context.player_raycast`)

Display name: **Player View Raycast** (id unchanged). Uses `WorldQueryAccess` (same gates as
world query `RaycastNode`):

| Gate | Result |
|------|--------|
| Origin block not loaded | `Valid=false` (fail closed) |
| Ray segment not fully loaded | valid miss (`Valid=true`, `Has Hit=false`, cleared hit outs) |
| Entity candidates `> GenerationLimits.MAX_ENTITY_QUERY_RESULTS` (4096) | `Valid=false` + `Error` |

| Hit kind | `Hit Block` | `Hit Entity` |
|----------|-------------|--------------|
| Block | `BlockInfoData` | `null` |
| Entity | `null` | `EntityInfoData` |
| Miss / invalid | `null` | `null` |

Miss vs invalid table unchanged from V32 for context-unavailable cases.

## Player Position Snapshot — lifecycle

| Path | Source |
|------|--------|
| `processNode(context)` first capture when empty | `ExecutionContext` / `ServerPlayerEntity` only |
| UI **Update Position** | `MinecraftClient.player` |
| `Use Eye Position` panel change | **property only** — no client recapture |

**Persistence:** `getNodeState` saves only `useEyePosition`. Cached XYZ / `hasCachedPosition` are
**session-local** and never written. Reload → no snapshot → `Valid=false` until Update / first
live capture. Legacy `hasCachedPosition` / `cachedX/Y/Z` keys are ignored on load.

Runtime missing player with a non-null context must **not** fall back to the client player
(Valid stays false until a successful capture).

## Deferred (P2)

- Dimension id vs `context.getWorld()` trait source coherence
- Entity raycast vanilla fidelity (`ProjectileUtil`)
- `output_time_ticks` LONG when a graph LONG type exists

## Migration

Graph **V111→V112** is a no-op (runtime payload / capture semantics; no wire remaps). Stamp-only.

## Contract

- `InputContextLanguageV2ContractTest` — V112 fence, type bindings, snapshot factories,
  Position context-only capture.
- `InputContextLanguageContractTest` — V32 inventory / fail-closed / non-persistent snapshot /
  eye-position setter retained.
