# Node Language v1 — Block State

**Status: PASSED / FROZEN** (HEAD `38af57f3`, Graph **V35**)

Language unification for exactly **4** `material.block_state.*` nodes: PURE state-only
semantics — compose, orient, merge, and resolve stair shape on existing placements without
voxelizing geometry or mutating `blockId`.

Shared helper: `BlockStateValidationUtils`. Graph schema: **V35** remaps
`block_state_assign` → `apply_block_state`, moves `slab_autofill` →
`material.directional_mapping.slab_stair_autofill`, removes three obsolete nodes, drops
geometry/deconstruct ports, and strips `blockId`/`id` from persisted `BlockStateData`.

Related: [`node-language-v1-type-selectors.md`](./node-language-v1-type-selectors.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **PURE** — all four nodes; no world writes, no voxelize, no placement creation.
2. **State only** — mutate `BlockPlacementData.stateData` only; `blockId` comes from upstream
   material mapping or `Build Block State` validation context, never from `BlockStateData`.
3. **No hidden defaults** — missing required inputs → `Valid=false` or empty pass-through;
   never fabricate `minecraft:stone`.
4. **Valid gate** — aligned with Type Selectors: `Registries.BLOCK.containsId`; registry
   unavailable → `Valid=false`.
5. **Merge, not replace** — Apply Block State merges override keys into each placement's
   existing state; base keys preserved when not overridden.
6. **Strict VECTOR** — Orient (and stair fallback direction) accept canonical `Vector3d` only;
   POINT / BLOCK_POS on VECTOR ports → `Valid=false`.

## Inventory (4)

| Display name | Type id | Role |
|--------------|---------|------|
| Build Block State | `material.block_state.build_block_state` | Compose + validate `BLOCK_STATE_DATA` |
| Orient Block State | `material.block_state.orient_block_state` | Vector → facing / axis / half |
| Apply Block State | `material.block_state.apply_block_state` | Merge state overrides into placements |
| Stair Shape | `material.block_state.stair_shape` | Neighborhood stair `shape` resolution |

All four are **PURE**.

**Removed from `material.block_state` (V35):**

| Legacy id | Replacement |
|-----------|-------------|
| `auto_orient_blocks` | Orient + Apply |
| `waterlogged` | Build `input_waterlogged` |
| `facing_from_normal` | Orient (VECTOR-only) |
| `block_state_assign` | `apply_block_state` |

**Moved:**

| Legacy id | New id |
|-----------|--------|
| `slab_autofill` | `material.directional_mapping.slab_stair_autofill` |

## BlockStateData contract

- Map of Minecraft **state property names → string values** only.
- **No** `blockId` / `id` keys on graph-facing outputs (V35 migration strips legacy keys).
- World write resolves block identity from `BlockPlacementData.blockId` separately.

## Build Block State

| Port | Type | Notes |
|------|------|-------|
| `input_base_state` | `BLOCK_STATE_DATA` | Optional copy base |
| `input_block_type` | `BLOCK_TYPE` | Required for validation |
| `input_property_name` / `input_property_value` | `STRING` | Dynamic single property |
| `input_facing` / `input_axis` / `input_half` | `STRING` | Shortcuts |
| `input_waterlogged` | `BOOLEAN` | Shortcut |
| `output_block_state` | `BLOCK_STATE_DATA` | Properties only |
| `output_property_count` | `INTEGER` | State keys only |
| `output_valid` / `output_error` | `BOOLEAN` / `STRING` | Registry validation |

Property `@NodeProperty propertiesText`: compact overrides (`facing=north,waterlogged=false`).

**Override precedence:** Base State → Properties Text → Dynamic name/value → Facing → Axis →
Half → Waterlogged (later wins).

**Valid=false when:** no block type, unknown block, registry unavailable, malformed
properties text (including empty key/value pairs like `facing=` or `=north`), or invalid
property values.

**Dropped:** `output_block_info` (`BLOCK_INFO` alias).

## Orient Block State

| Port | Type | Notes |
|------|------|-------|
| `input_vector` | `VECTOR` | Canonical `Vector3d` only; non-finite components → `Valid=false` |
| `input_mode` | `STRING` | `facing`, `horizontal_facing`, `axis`, `stair`; unknown mode → `Valid=false` |
| `input_block_type` | `BLOCK_TYPE` | Optional; when set, validates derived properties |
| `output_block_state` | `BLOCK_STATE_DATA` | Oriented properties |
| `output_valid` / `output_error` | `BOOLEAN` / `STRING` | Invalid vector or validation failure |

## Apply Block State

| Port | Type | Notes |
|------|------|-------|
| `input_placements` | `BLOCK_PLACEMENT_LIST` | Required |
| `input_block_state` | `BLOCK_STATE_DATA` | Override map merged per placement |
| `output_placements` | `BLOCK_PLACEMENT_LIST` | Same positions and block ids |

**Dropped:** geometry/coordinates/block_type inputs, `output_positions`, `output_block_ids`.

## Stair Shape

| Port | Type | Notes |
|------|------|-------|
| `input_placements` | `BLOCK_PLACEMENT_LIST` | Stair placements |
| `input_direction` | `VECTOR` | Fallback facing when state lacks facing |
| `input_half` | `STRING` | Optional half override |
| `output_placements` | `BLOCK_PLACEMENT_LIST` | Stair placements get `shape` / facing / half; non-stair pass through unchanged |

## Typical chain

```
Voxelize / Assign Block Type  →  Material mapping  →  Apply Block State
Build Block State  ────────────────────────────────────────┘
Orient Block State ────────────────────────────────────────┘
Apply Block State  →  Stair Shape  →  Preview / Apply Changes
```

Slab / stair geometry adaptation belongs upstream in
`material.directional_mapping.slab_stair_autofill` (may change `blockId`).

## Graph migration (V34→V35)

| Action | Detail |
|--------|--------|
| Type remap | `block_state_assign` → `apply_block_state` |
| Type remap | `slab_autofill` → `directional_mapping.slab_stair_autofill` |
| Delete nodes | `auto_orient_blocks`, `waterlogged`, `facing_from_normal` |
| Drop wires | `output_block_info`, geometry ports on Apply/Stair, deconstruct outputs |
| List tighten | slab normals `LIST` → `VECTOR_LIST` incompatible wires dropped |
| State clean | strip `blockId`/`id` from saved node state maps |

## Contracts

- `BlockStateLanguageContractTest` — 4-node inventory, PURE, no geometry ports, Valid gates
  (missing block type, unknown block, malformed properties text including empty key/value,
  non-finite VECTOR, unknown orient mode), merge semantics, strict VECTOR / POINT rejection,
  stair-only shape mutation, V34→V35 migration.
- `BlockStateNodeTest` — Build/Orient property composition.
- Format contract tests bumped to **V35**.
