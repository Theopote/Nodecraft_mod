# Node Language v2 — Material Basic Assignment

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only `1`; V114 remains historical residue)

Freeze for `material.basic_assignment.*` (4 nodes): Create Block Palette, Assign Block Type,
Block Palette, Weighted Spatial Palette. Scheme A remap (preserve `stateData`, validate
against the new `blockId`, fail closed). Palette ingest is `Identifier.tryParse` syntax
only; registry membership is enforced on Build / Apply / remap validation.

Related: [`node-language-v2-basic-assignment.md`](./node-language-v2-basic-assignment.md)
(V114 source/weight fence), [`node-language-v2-material-block-state.md`](./node-language-v2-material-block-state.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Inventory

| Node | Role |
|------|------|
| Create Block Palette | STRING_LIST + optional DOUBLE_LIST / Block A–D → `BLOCK_PALETTE` |
| Assign Block Type | Remap every placement `blockId`; preserve `stateData` |
| Block Palette | Cyclic remap through typed palette |
| Weighted Spatial Palette | Spatially coherent weighted field (value noise + seed) |

Main chain payload: `BLOCK_PLACEMENT_LIST`. Palette payload: `BlockPaletteData`.

PURE except immutable runtime registry lookups on remap validation (future `REGISTRY_READ`).
No `WORLD_READ` retag.

## Valid / Error

All four nodes emit `output_valid` / `output_error`. Failure is transactional: empty
placement list (or empty palette + size 0), never a partial remap.

## Remap Scheme A

`MaterialMappingSupport.remapBlockId` is a pure id rewrite. Callers then
`BlockStateValidationUtils.remapIncompatibility`:

- empty / null `stateData` → ok
- otherwise `validateProperties(newBlockId, state)` (registry + `Property.parse`)
- first failure → `[]`, `Valid=false`

Stairs remapped to `stone` while keeping `facing` fail closed. New voxel rows with empty
state stay valid.

## Palette construction vs registry

Create Palette / `BlockPaletteEntry` compact ctor:

- non-blank id, `minecraft:` normalize, `Identifier.tryParse`
- finite `weight >= 0`
- blank / non-string / failed parse → invalid (no silent drop)
- optional Block A–D: unconnected skip, connected-invalid fail
- cap `GenerationLimits.MAX_BLOCK_PALETTE_ENTRIES` (1024)

Registry existence is **not** required at Create time.

`BlockPaletteData` graph construction does not silently filter null/illegal entries
(`canonical(...)` returns null). `ofBlockIds` / `fromLegacyObject` remain legacy (P3).

## Seed

Weighted Seed uses `RandomInputResolver` via `BasicAssignmentUtils.resolveSeed`:

| Port | Behavior |
|------|----------|
| undriven | `0` (valid) |
| driven exact `Integer` | that seed |
| driven `"0"` / other types | `Valid=false` |

Sampling remains value noise (spatially coherent field), not IID / `HASH_RANDOM`.

## Contract

- `BasicAssignmentLanguageV2ContractTest` — remap fail-closed, palette ingest, strict seed
- `BlockPaletteDataTest` — ctor invariants
- `BasicAssignmentLanguageContractTest` — V40 inventory retained
