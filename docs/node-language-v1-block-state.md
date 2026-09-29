# Node Language v1 — Block State

**Status: PASSED / FROZEN** (historical Graph **V35**; see v2 remediation **V115**)

Language unification for exactly **4** `material.block_state.*` nodes: PURE state-only
semantics — compose, orient, merge, and resolve stair shape on existing placements without
voxelizing geometry or mutating `blockId`.

Shared helper: `BlockStateValidationUtils`. Graph schema: **V35** remaps
`block_state_assign` → `apply_block_state`, moves `slab_autofill` →
`material.directional_mapping.slab_stair_autofill`, removes three obsolete nodes, drops
geometry/deconstruct ports, and strips `blockId`/`id` from persisted `BlockStateData`.

**V115 remediation:** Apply/Stair Valid+Error, fail-closed placements, Direction/Half
OptionalPortDrive, Property/Value pairs, VectorData VECTOR, StairsBlock detection —
see [`node-language-v2-block-state.md`](./node-language-v2-block-state.md).

Related: [`node-language-v1-type-selectors.md`](./node-language-v1-type-selectors.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **PURE** — all four nodes; no world writes, no voxelize, no placement creation.
2. **State only** — mutate `BlockPlacementData.stateData` only; `blockId` comes from upstream
   material mapping or `Build Block State` validation context, never from `BlockStateData`.
3. **No hidden defaults** — missing required inputs → `Valid=false` or empty pass-through;
   never fabricate `minecraft:stone`; never invent Direction=`north` / Half=`bottom` from
   connected-invalid ports.
4. **Valid gate** — aligned with Type Selectors: `Registries.BLOCK.containsId`; registry
   unavailable → `Valid=false`.
5. **Merge, not replace** — Apply Block State merges override keys into each placement's
   existing state; base keys preserved when not overridden.
6. **Strict VECTOR** — Orient and Stair Direction accept `VectorData` / finite `Vector3d`;
   POINT / BLOCK_POS on VECTOR ports → `Valid=false`.
7. **Fail-closed placements** — mixed / incomplete `BLOCK_PLACEMENT_LIST` → whole-node fail
   (Apply and Stair Shape).

## Inventory (4)

| Display name | Type id | Role |
|--------------|---------|------|
| Build Block State | `material.block_state.build_block_state` | Compose + validate `BLOCK_STATE_DATA` |
| Orient Block State | `material.block_state.orient_block_state` | Vector → facing / axis / half |
| Apply Block State | `material.block_state.apply_block_state` | Merge state overrides into placements |
| Stair Shape | `material.block_state.stair_shape` | Neighborhood stair `shape` resolution |

All four are **PURE**. Apply and Stair Shape expose `output_valid` / `output_error` (V115).

**Removed from `material.block_state` (V35):**

| Legacy id | Replacement |
|-----------|-------------|
| `auto_orient_blocks` | Orient + Apply |
| `waterlogged` | Build `input_waterlogged` |
| `facing_from_normal` | Orient (VECTOR-only) |
| `block_state_assign` | `apply_block_state` |

## Contracts

- `BlockStateLanguageContractTest` — V35 inventory / PURE / merge / migration.
- `BlockStateLanguageV2ContractTest` — V115 fence (see v2 doc).
- Format fences bumped historically to **V35**; current pin is **V115**.
