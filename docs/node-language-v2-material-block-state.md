# Node Language v2 — Material Block State

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only `1`; V115 remains historical residue)

Freeze for `material.block_state.*` (4 nodes): Build Block State, Apply Block State,
Orient Block State, Stair Shape. Per-placement registry validation on Apply; Build never
emits a usable illegal map; `BlockStateData` is an immutable value object.

Related: [`node-language-v2-block-state.md`](./node-language-v2-block-state.md)
(V115 Apply/Stair fence), [`node-language-v2-material-basic-assignment.md`](./node-language-v2-material-basic-assignment.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Inventory

| Node | Role |
|------|------|
| Build Block State | Compose + registry-validate property map |
| Apply Block State | Merge override into placements; validate per `placement.blockId` |
| Orient Block State | Derive facing/axis/half from VECTOR |
| Stair Shape | Neighborhood stair `shape` |

PURE except immutable runtime registry lookups (future `REGISTRY_READ`). No `WORLD_READ` retag.

## Apply

Optional override:

| State | Behavior |
|-------|----------|
| unconnected | pass-through (no override merge) |
| connected / present + `BlockStateData` | merge; strip identity keys |
| connected / present + not `BlockStateData` | `Valid=false`, `[]` |

After merge, **`validateProperties(placement.blockId(), merged)` per item**. Mixed
`oak_stairs` / `stone` cannot share one check. Empty state is compatible. First failure
aborts the whole list.

## Build

Invalid → `output_block_state=null`, `property_count=0`, `Valid=false`.

| Port | Unconnected | Connected |
|------|-------------|-----------|
| Base State | empty | exact `BlockStateData` else fail |
| Facing / Axis / Half | skip | exact non-blank `String` else fail |
| Waterlogged | skip | exact `Boolean` else fail |

Do not use `OptionalPortDrive.resolveOptionalBoolean(..., false)` for Waterlogged
(unconnected must not write `false`). Property+Value remain paired. Registry
`validateProperties` still gates success.

## BlockStateData

Immutable wrapper: canonical non-blank keys/values, identity keys (`blockId`/`id`)
stripped in the factory, `withProperty` / `merge` return new instances, `properties()`
unmodifiable. Semantic checks stay in `BlockStateValidationUtils`.

## Stair / Orient

Stair Direction/Half OptionalPortDrive rules from V115 unchanged. Orient writes via
`withProperty` (no in-place mutation).

## Contract

- `BlockStateLanguageV2ContractTest` — Apply per-id validation, connected-invalid override,
  Build null-on-invalid / shortcut types
- `BlockStateLanguageContractTest` — V35 inventory retained
- `BlockStateNodeTest` — Build invalid emits null state
