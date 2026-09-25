# Node Language v1 — Basic Assignment

**Status: PASSED / FROZEN** (Graph **V40**)

Language unification for exactly **4** `material.basic_assignment.*` nodes: PURE
material entry layer producing canonical `BLOCK_PLACEMENT_LIST` payloads,
typed `BLOCK_PALETTE` construction, fail-closed list validation, and
`RandomOps` for weighted selection — no hidden `minecraft:stone` defaults.

Shared helpers: `BasicAssignmentUtils` + `MaterialMappingSupport`. Graph schema:
**V40** drops deconstruct outputs, removes Fallback Block Type ports, and
tightens Create/Weighted list ports to `STRING_LIST` / `DOUBLE_LIST`.

Related: [`node-language-v1-surface-aging.md`](./node-language-v1-surface-aging.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **PURE** — all four nodes.
2. **blockId only** — `pos` and `stateData` unchanged on assignment/remap.
3. **Canonical payloads** — `BLOCK_PLACEMENT_LIST` out; `BLOCK_PALETTE` for palettes.
4. **No hidden vanilla** — never default to `minecraft:stone`.
5. **No deconstruct outputs** — drop `output_positions`, `output_block_ids`, and tree mirrors.
6. **Valid / Error** — all four nodes expose fail-closed validation gates.
7. **Palette model** — `BlockPaletteEntry { blockId, weight }` only (no `stateData`).

## Inventory (4)

| Display name | Type id | Role |
|--------------|---------|------|
| Assign Block Type | `material.basic_assignment.assign_block_type` | Single block type → placements |
| Create Block Palette | `material.basic_assignment.create_block_palette` | Build `BLOCK_PALETTE` |
| Block Palette | `material.basic_assignment.block_palette` | Cyclic palette assignment |
| Weighted Block Palette | `material.basic_assignment.weighted_palette` | Weighted random by position + seed |

## Assign Block Type

- **Block Type required** always (placements and geometry/coords/tree).
- Outputs: `output_placements`, `output_placements_tree`, `output_valid`, `output_error`.

## Create Block Palette

- `input_block_ids : STRING_LIST` — strict strings; mixed list → invalid.
- `input_weights : DOUBLE_LIST` — optional; when connected, count must match block ids.
- Weights: finite, `>= 0`, sum `> 0` when provided.
- Block A/B/C/D appended after list entries (order frozen in contract).
- Empty palette → `Valid=true` (consumer decides geometry invalidity).

## Block Palette

- No Fallback Block Type port.
- **Placements + empty palette** → preserve `source.blockId`.
- **Geometry/coords + empty palette** → `Valid=false`.
- **Flat**: per-item cyclic index (`startIndex + itemIndex`).
- **Tree**: per-branch cyclic index (`startIndex + branchIndex`).
- **Start Index**: `INTEGER` only (no `Number` coercion).

## Weighted Block Palette

- No Fallback Block Type port.
- `input_weights : DOUBLE_LIST` override — strict size match with palette; no pad/truncate.
- Missing Weights port → use palette embedded weights.
- Random: `RandomOps.valueNoise3(x,y,z,seed)` → cumulative weight pick (**position + seed only**).
- Seed: `RandomOps.resolveSeed` (Integer-only).
- Outputs include `output_total_weight` diagnostic.

## Graph migration (V39→V40)

| Action | Detail |
|--------|--------|
| Drop wires | Deconstruct outputs from assign / block_palette / weighted_palette |
| Drop wires | `input_fallback_block_type` on palette nodes |
| Tighten | Incompatible LIST wires to Create/Weighted typed list ports |

## Contracts

- `BasicAssignmentLanguageContractTest` — inventory, PURE, no stone, typed lists,
  flat vs tree cyclic semantics, RandomOps position stability, V39→V40 migration.
- Format fences bumped to **V40**.
