# Node Language v1 — Type Selectors

**Status: PASSED / FROZEN** (post-implementation working tree, Graph **V33**)

Language unification for `input.type_selectors.*` (4 nodes): symmetric registry type selection with
`output_valid`, canonical `namespace:path` ids, and UI-only picker filters.

Shared helper: `RegistrySelectorUtils`. Graph schema: **V33** retargets Block Type to
`BLOCK_TYPE`, adds `output_valid` on all four selectors, removes Block State Selector, and
migrates legacy graphs to Block Type + Build Block State.

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`node-language-v1-input-context.md`](./node-language-v1-input-context.md).

## Core rules

1. **`output_valid` gate** — `true` only when the id is syntactically valid, present in the
   active registry, and satisfies the allow-modded policy. Missing mods or empty registries
   preserve the saved canonical id with `Valid=false` (no silent fallback to defaults).
2. **Canonical ids** — graph-facing output is always `namespace:path` (lowercase). Bare names
   normalize to `minecraft:` on input.
3. **Filters are UI-only** — `allowModded`, category, search, and minecraft-only scope affect
   the picker list only; they must not rewrite a saved selection.
4. **PURE effect** — selectors are graph parameters; registry lookups are editor validation only.
5. **Block state construction** — use **Block Type Selector → Build Block State**, not a combined
   type-selector node.

## Inventory (4)

| Display name | Type id | Primary output | Java payload |
|--------------|---------|----------------|--------------|
| Block Type Selector | `input.type_selectors.block_type_selector` | `output_block_id` (`BLOCK_TYPE`) | `String` |
| Entity Type Selector | `input.type_selectors.entity_type_selector` | `output_entity_id` (`ENTITY_TYPE`) | `String` |
| Item Type Selector | `input.type_selectors.item_type_selector` | `output_item_id` (`ITEM_TYPE`) | `String` |
| Biome Selector | `input.type_selectors.biome_selector` | `output_biome_id` (`BIOME`) | `String` |

All four also expose: `output_namespace`, `output_*_path`, `output_is_modded`, `output_valid`.

**Removed:** `input.type_selectors.block_state_selector` — use
`material.block_state.build_block_state` with optional **Properties Text** instead.

## Shared output shape

| Port | Type | Notes |
|------|------|-------|
| Primary type port | `BLOCK_TYPE` / `ENTITY_TYPE` / `ITEM_TYPE` / `BIOME` | Canonical id string |
| `output_namespace` | `STRING` | Namespace segment |
| `output_*_path` | `STRING` | Path segment |
| `output_is_modded` | `BOOLEAN` | `namespace != minecraft` |
| `output_valid` | `BOOLEAN` | Registry + policy gate |

## Valid semantics

| Condition | Primary output | `Valid` |
|-----------|----------------|---------|
| Known id in registry, policy ok | canonical id | `true` |
| Unknown / missing mod id | **preserved** canonical id | `false` |
| Registry not ready | preserved id | `false` |
| Modded id + `allowModded=false` | preserved id | `false` |
| Invalid syntax on restore | default id (display) | `false` |

## Block state workflow

```
Block Type Selector  →  Build Block State  →  BLOCK_STATE_DATA / BLOCK_INFO
```

**Build Block State** (`material.block_state.build_block_state`):

- `input_block_type`: `BLOCK_TYPE`
- `@NodeProperty propertiesText`: compact overrides (`facing=north,waterlogged=false`)
- Port overrides still apply after properties text
- `output_valid` / `output_error` validate against real block property definitions

## Shared helpers (`RegistrySelectorUtils`)

| Helper | Role |
|--------|------|
| `normalizeCanonicalId` | Syntax-only normalization; `null` when blank/unparsable |
| `splitNamespacePath` | Namespace + path for outputs |
| `isModdedNamespace` | `namespace != minecraft` |
| `computeValid` | Registry + allow-modded policy |

## Graph migration (V32→V33)

| Action | Detail |
|--------|--------|
| Block Type port | `output_block_id`: `STRING` → `BLOCK_TYPE`; drop incompatible wires |
| Valid port | Added on four selectors — no wire migration |
| Block State Selector | Remap → Block Type Selector; split state output into inserted Build Block State node |
| Legacy state | `blockId` → `selectedBlock`; `stateProperties` → build node `propertiesText` |

## Contracts

- `TypeSelectorsLanguageContractTest` — 4-node inventory, PURE, `BLOCK_TYPE`, Valid,
  unknown-id preservation, UI-only filter, V32→V33 migration.
- `BlockStateNodeTest` — Build Block State `propertiesText` validation.
- Format contract tests bumped to **V33**.
