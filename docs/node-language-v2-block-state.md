# Node Language v2 — Block State

**Status: PASSED / FROZEN** (Graph **V115**; V35 remains historical v1)

Strict Apply & Stair input remediation for `material.block_state.*`: fail-closed
placements, Valid/Error on Apply and Stair Shape, OptionalPortDrive for Direction/Half,
paired Build Property/Value, VectorData-aware VECTOR resolve, and StairsBlock registry
detection.

Related: [`node-language-v1-block-state.md`](./node-language-v1-block-state.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Apply Block State

Ports: `output_placements`, `output_valid`, `output_error`.

| Condition | placements | Valid |
|-----------|------------|-------|
| Strict parse fail (mixed / blank id / null pos) | `[]` | false |
| Success (including empty) | merged list | true |

Merge semantics unchanged (`pos` / `blockId` preserved; state keys merge not replace).

**Caveat:** Apply does **not** re-validate that override properties are legal for every
placement `blockId`. Compatibility is the responsibility of Build (typed context) and
world-write consumers.

## Stair Shape

Ports: `output_placements`, `output_valid`, `output_error`. Same strict placements parse.

### Direction (`input_direction`)

| State | Behavior |
|-------|----------|
| Undriven | no facing fallback |
| Driven + finite non-zero horizontal vector | fallback facing (dominant X/Z) |
| Driven + null / invalid / zero | `Valid=false` |

Stair placements lacking `facing` in state when Direction is undriven → `Valid=false`
(`Stair facing required`). Never invent `north` from a bad Direction port.

### Half (`input_half`)

| State | Behavior |
|-------|----------|
| Undriven | preserve placement half; absent → `"bottom"` for neighborhood math |
| Driven `"top"` / `"bottom"` | override |
| Driven other | `Valid=false` |

### Stair detection

`Registries.BLOCK.get(id) instanceof StairsBlock`. Unknown id → non-stair pass-through.

## Build Property / Value pair

| Name | Value | Result |
|------|-------|--------|
| both undriven | — | skip |
| both driven + non-blank strings | apply |
| only one driven | `Valid=false` |
| driven but not usable String | `Valid=false` |

## VECTOR runtime

`resolveStrictVector3d` accepts `VectorData` and finite `Vector3d` via
`SpatialValueResolver.resolveVector` (Orient + Stair Direction).

## Migration

Graph **V114→V115** is a no-op (new Valid/Error outputs; no wire remaps).

## Contract

- `BlockStateLanguageV2ContractTest` — V115 fence, strict placements, Direction/Half,
  Property/Value pairs, VectorData, non-stair pass-through.
- `BlockStateLanguageContractTest` — V35 inventory retained.
