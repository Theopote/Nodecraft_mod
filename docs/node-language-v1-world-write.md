# World Write — Node Language v1

**Status: PASSED / FROZEN** (Graph **V64**)

World Write v1 freezes the language boundary for the 18 `world.write` nodes.
Destructive side effects follow: Validate → Bound → Execute → Snapshot → Report → Undo/Redo.

## Spatial / typed inputs

- `BLOCK_POS` only for block coordinates — **no** Point/Vector floor coercion
- `BLOCK_LIST` via strict ordered resolver (`BlockListUtils.resolveStrictBlockList`)
- `BLOCK_INFO_LIST` size must equal Coordinates size (no cyclic reuse)
- Entity mutate: `MINECRAFT_ENTITY` / `MINECRAFT_ENTITY_LIST` only (no UUID/type lookup)
- Spawn / Teleport destination: `POINT` only
- Continuous scalars: `DOUBLE` (Yaw/Pitch degrees, finite)

## Trigger contract

- Unconnected → property (default **false**; V63→V64 migrates mutators to `trigger=true`)
- Connected true → run
- Connected false → skip (`Valid=true`, no mutation)
- Connected null/invalid → **fail closed** (`Valid=false`, no mutation)

## GenerationLimits (world write)

- `MAX_WORLD_WRITE_BLOCKS` = 262_144
- `MAX_WORLD_WRITE_ENTITIES` = 4_096
- `MAX_WORLD_WRITE_SNBT_CHARS` = 65_536
- `MAX_WORLD_WRITE_COMMAND_CHARS` = 1_024

User budget `> hard cap` or invalid exact INTEGER → `Valid=false`. Never clamp. Never `≤0 → unlimited`.
Region volume uses `Math.multiplyExact` (overflow → fail closed).

## Transaction / Undo

- `BlockSnapshot { pos, BlockState, NBT? }`
- Undo stack keyed by `(actorId, worldKey)` (dimension registry id)
- Undo/Redo restore state + block-entity NBT
- World key mismatch → fail closed; record kept
- Partial apply → `Complete=false`; remaining work kept on stack

## Effects

| Node | Effect |
|------|--------|
| Peek Last Undo | `CONTEXT_READ` |
| Clear Undo History | `CONTEXT_WRITE` |
| Undo / Redo + all mutators | `WORLD_WRITE` |

Preview still forbids `WORLD_WRITE` (inherits nested skip).

## Canonical inventory (order 0–17)

| Order | Display | Id |
|------:|---------|-----|
| 0 | Set Block | `world.write.set_block` |
| 1 | Set Blocks | `world.write.set_blocks` |
| 2 | Fill Region | `world.write.fill_region` |
| 3 | Replace Blocks | `world.write.replace_blocks` |
| 4 | Clone Region | `world.write.clone_region` |
| 5 | Clear Blocks | `world.write.remove_blocks` |
| 6 | Set Block NBT | `world.write.set_block_nbt` |
| 7 | Spawn Entity | `world.write.spawn_entity` |
| 8 | Teleport Entity | `world.write.entity_teleport` |
| 9 | Remove Entities | `world.write.remove_entities` |
| 10 | Write Sign Text | `world.write.write_sign_text` |
| 11 | Apply Redstone Power | `world.write.apply_redstone_power` |
| 12 | Simulate Right Click | `world.write.simulate_right_click` |
| 13 | Execute Command | `world.write.execute_command` |
| 14 | Undo Last World Write | `world.write.undo_last_write` |
| 15 | Redo Last World Write | `world.write.redo_last_write` |
| 16 | Peek Last World Write Undo | `world.write.peek_last_undo` |
| 17 | Clear World Write Undo History | `world.write.clear_undo_history` |

All mutators expose `Valid` / `Error`. Batch writes also expose `Complete` / `Hit Limit` / counts.

### Partial write semantics

- Preflight failure → `Valid=false`, 0 writes
- After first mutation, per-cell failures → `Valid=true`, `Complete=false`, Success/Failure counts

### Execute Command

- Privileged escape hatch gated by `WorldWriteCommandPolicy` (**default denied**)
- Requires player executor (no `PermissionPredicate.ALL` server fallback)
- Non-empty command; length ≤ `MAX_WORLD_WRITE_COMMAND_CHARS`

### Removed dead inputs (V64)

- Set Blocks `Batch Updates`
- Apply Redstone `Play Sound`
- Teleport Dimension / AllowAcrossDimension
- Remove Entities UUID / Entity Type lookup ports

## Migration (V63 → V64)

- Inject `trigger=true` state on existing write mutators
- Drop wires to removed ports listed above

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.WorldWriteLanguageContractTest"
```
