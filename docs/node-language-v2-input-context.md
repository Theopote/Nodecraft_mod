# Node Language v2 — Input Context

**Status: PASSED / FROZEN** (Graph **V112**; V32 remains historical v1)

Snapshot-data modernization for Player Raycast and Player Position capture boundaries.
Current Time and Dimension Info unchanged from V32 fail-closed contracts.

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

## Player Raycast

| Hit kind | `Hit Block` | `Hit Entity` |
|----------|-------------|--------------|
| Block | `BlockInfoData` | `null` |
| Entity | `null` | `EntityInfoData` |
| Miss / invalid | `null` | `null` |

Miss vs invalid table unchanged from V32.

## Player Position Snapshot — capture split

| Path | Source |
|------|--------|
| `processNode(context)` first capture | `ExecutionContext` / `ServerPlayerEntity` only |
| UI **Update Position** | `MinecraftClient.player` |
| `Use Eye Position` panel change | client recapture |

Runtime missing player with a non-null context must **not** fall back to the client player
(Valid stays false until a successful capture).

## Deferred (P2)

- Dimension id vs `context.getWorld()` trait source coherence
- Entity raycast vanilla fidelity (`ProjectileUtil`)
- `output_time_ticks` LONG when a graph LONG type exists

## Migration

Graph **V111→V112** is a no-op (runtime payload / capture semantics; no wire remaps).

## Contract

- `InputContextLanguageV2ContractTest` — V112 fence, type bindings, snapshot factories,
  Position context-only capture.
- `InputContextLanguageContractTest` — V32 inventory / fail-closed retained.
