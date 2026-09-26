# Node Language v1 — World Query

**Status: PASSED / FROZEN** (Graph **V60**)

Continuous geometry ↔ discrete Minecraft world reads under `world.query` (11 nodes).

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`node-language-v1-input-context.md`](./node-language-v1-input-context.md),
Spatial Convention v1 (`BlockSpace` cell-center lattice).

## Product boundary

```text
grid = block cell-center lattice (n + 0.5), not integer-corner round(x)
BLOCK_POS index → POINT at cell center via BlockSpace
PURE predicates never touch ExecutionContext world
WORLD_READ nodes require context + world; bounded volume / count caps
strict typed ports: POINT_LIST, VECTOR_LIST, MINECRAFT_ENTITY_LIST, STRING_LIST
OptionalPortDrive on optional booleans / distances: unconnected → property/default; connected-null → fail closed
Valid + Error outputs on query nodes that can reject input
no hidden POINT → BLOCK_POS floor in spatial filters
```

## Grid semantics (V60 freeze)

A point is **on-grid** when it lies on the block **cell-center** lattice:

```text
x = blockIndexX + 0.5
y = blockIndexY + 0.5
z = blockIndexZ + 0.5
```

Implementation: `BlockSpace.isCellCenter`, `nearestCellBlockPos`, `offsetFromNearestCellCenter`.

Integer corners such as `(0, 0, 0)` are **off-grid**. `(0.5, 0.5, 0.5)` is on-grid for cell `(0,0,0)`.

Grid nodes **classify only** — no silent snap-to-grid.

## Inventory (11)

| order | Display name | Type id | Effect |
|------:|--------------|---------|--------|
| 0 | Is Grid Point | `world.query.is_grid_point` | `PURE` |
| 1 | Filter Grid Points | `world.query.filter_grid_points` | `PURE` |
| 2 | Point In Region | `world.query.is_point_in_region` | `PURE` |
| 3 | Get Neighbor Blocks | `world.query.get_neighbors` | `WORLD_READ` |
| 4 | Flood Fill | `world.query.flood_fill` | `WORLD_READ` |
| 5 | Raycast | `world.query.raycast` | `WORLD_READ` |
| 6 | Get Light Level | `world.query.get_light_level` | `WORLD_READ` |
| 7 | Get Fluid Level | `world.query.get_fluid_level` | `WORLD_READ` |
| 8 | Filter Points By Rule | `world.query.filter_points_by_rule` | `PURE` |
| 9 | Get Entities In Region | `world.query.get_entities_in_region` | `WORLD_READ` |
| 10 | Get Entity | `world.query.get_entity` | `WORLD_READ` |

## Pure spatial nodes

### Is Grid Point

- Input: `Point` (`POINT`)
- Outputs: `Is Grid Point`, `Valid`, `Nearest Coordinate` (`BLOCK_POS`), `Offset Vector`, `Distance`
- Property: tolerance (default `1e-6`)

### Filter Grid Points

- Input: strict `POINT_LIST`
- Outputs: `Grid Points` / `Off-Grid Points` (`POINT_LIST`), `Grid Blocks` (`BLOCK_LIST`), counts, `Valid`, `Error`
- Removed V60: `Skipped Count` output

### Point In Region

- Input: `Point` (`BLOCK_POS` — tests **cell center** of that block), `Region`
- Pure region membership; no world access

### Filter Points By Rule

- Inputs: `Points` (`POINT_LIST`), optional `Normals` (`VECTOR_LIST`), height/slope bounds, optional `Invert`
- Outputs: filtered/removed **point lists**, `Mask` (`BOOLEAN_LIST`), `Slopes` (`DOUBLE_LIST`), counts, `Valid`, `Error`
- `Mode` is a **node property** (`ALL` / `ANY`), not an input port
- Removed V60: `Filtered Blocks`, `Removed Blocks`, `Mode` input port

## World-read nodes

### Get Neighbor Blocks

- Center: `BLOCK_POS`; radius: exact `INTEGER` ≥ 1
- Optional `Include Diagonals` (`OptionalPortDrive`, default false)
- Pre-check estimated volume against `GenerationLimits.MAX_NEIGHBOR_QUERY_BLOCKS` (262144)
- Block IDs: `STRING_LIST`

### Flood Fill

- Seed: `BLOCK_POS`; `Max Distance` / `Max Blocks`: exact `INTEGER`
- `Max Blocks` hard cap: `GenerationLimits.MAX_FLOOD_FILL_BLOCKS` (262144)
- Outputs include `Complete`, `Hit Limit`, `Stopped Reason`, `Valid`, `Error`
- Offset stepping uses `BlockPosMath.tryOffset` (overflow-safe)

### Raycast

- Origin / Hit Position: `POINT`; Direction / Hit Normal: `VECTOR`
- `Max Distance` / `Entity Radius`: finite; optional drives where applicable
- Block raycast requires player in execution context — **fail closed** when missing (no silent skip)
- Hit Entity: `MINECRAFT_ENTITY`

### Get Light Level / Get Fluid Level

- Standard world-read validation; order 6 / 7

### Get Entities In Region

- Typed outputs: `Entities List` (`MINECRAFT_ENTITY_LIST`), `Entity Type IDs` (`STRING_LIST`), `Entity Positions` (`POINT_LIST`)
- Filter booleans: `OptionalPortDrive` with property fallback

### Get Entity

- UUID connected → UUID lookup; else entity type + nearest search near player
- `Find Nearest` / `Max Distance`: `OptionalPortDrive`
- Entity position output: `POINT`

## Shared helpers

- `BlockSpace` — cell-center grid helpers
- `BlockPosMath.tryOffset` — safe block offset
- `GenerationLimits.MAX_NEIGHBOR_QUERY_BLOCKS`, `MAX_FLOOD_FILL_BLOCKS`, `estimateCubeVolume`
- `PointUtils.resolveStrictPointList`, `VectorUtils.resolveStrictVectorList`
- `OptionalPortDrive`

## Migration V59→V60

`migrateV59ToV60` drops wires from removed ports:

| Node | Removed ports |
|------|----------------|
| Filter Points By Rule | `output_filtered_blocks`, `output_removed_blocks`, `input_mode` |
| Filter Grid Points | `output_skipped_count` |

Contract: `WorldQueryLanguageContractTest`.
