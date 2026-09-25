# Node Language v1 — Directional Mapping

**Status: PASSED / FROZEN** (Graph **V36**)

Language unification for exactly **3** `material.directional_mapping.*` nodes: PURE
blockId-only remapping driven by column geometry or surface normals, without mutating
`stateData` or fabricating hidden vanilla materials.

Shared helper: `MaterialMappingSupport`. Graph schema: **V36** drops
`output_positions` / `output_block_ids` wires from the three directional nodes.

Related: [`node-language-v1-block-state.md`](./node-language-v1-block-state.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **PURE** — all three nodes.
2. **blockId only** — `pos` and `stateData` unchanged; only `blockId` may change.
3. **Preserve on partial override** — when a material port is unconnected and the source is
   existing placements, keep `source.blockId` (never invent `minecraft:stone`).
4. **Geometry / coordinates path** — requires at least one explicit material port; otherwise
   `Valid=false`, empty placements.
5. **No deconstruct duplicate outputs** — canonical output is `BLOCK_PLACEMENT_LIST` plus
   `Valid` / `Error` (Slab/Stair also keeps counts).
6. **Normals fail-closed** — Slab/Stair Adapt requires index-aligned `VECTOR_LIST` when
   adapting existing placements.

## Inventory (3)

| Display name | Type id | Role |
|--------------|---------|------|
| Column Layer Map | `material.directional_mapping.top_side_bottom_map` | X/Z column stratification |
| Surface Slope Map | `material.directional_mapping.slope_map` | 4-neighbor height grade on column tops |
| Slab / Stair Adapt | `material.directional_mapping.slab_stair_autofill` | Normal → full/slab/stair blockId |

Type ids are unchanged (display rename only). All three are **PURE**.

## Column Layer Map

Per X/Z column: `maxY` → Top, `minY` → Bottom, else Side.
Single-cell column (`minY == maxY`) → **Top wins**.

Unconnected Top/Side/Bottom ports preserve `source.blockId`.

## Surface Slope Map

4-neighbor max `|ΔY|` on column tops: 0 → Flat, 1 → Slope, ≥2 → Steep.
**Only** voxels where `pos.y == columnTop` are remapped; interior voxels pass through.

## Slab / Stair Adapt

Normal angle from vertical (degrees):

| Band | Condition | Material port |
|------|-----------|---------------|
| Default (full) | `angle ≤ Slab Angle` | `input_default_block` |
| Slab | `Slab Angle < angle ≤ Stair Angle` | `input_slab_block` |
| Stair | `angle > Stair Angle` | `input_stair_block` |

Defaults: Slab Angle = 20°, Stair Angle = 35° (clamped to `[0, 90]`).

Does **not** write `type` / `half` / `shape` / `facing`. Chain:

```
Placements → Slab/Stair Adapt → Orient Block State → Apply Block State → Stair Shape
```

## Graph migration (V35→V36)

| Action | Detail |
|--------|--------|
| Drop wires | `output_positions`, `output_block_ids` from all three directional_mapping nodes |
| No type remap | ids unchanged |

## Contracts

- `DirectionalMappingLanguageContractTest` — inventory, PURE, blockId-only, normals fail-closed,
  surface-only slope, partial preserve, V35→V36 migration.
- `MaterialFamilyContractTest` — slope surface vs interior.
- Format fences bumped to **V36**.
