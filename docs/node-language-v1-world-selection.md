# Node Language v1 — World Selection

**Status: PASSED / FROZEN** (Graph **V62**)

Editor selection sources and pure Point↔Block spatial helpers under `world.selection` (8 nodes).

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`node-language-v1-world-query.md`](./node-language-v1-world-query.md) (V60),
[`node-language-v1-world-read.md`](./node-language-v1-world-read.md) (V61),
Spatial Convention v1 (`BlockSpace` cell-center lattice).

## Product boundary

```text
POINT = position; VECTOR = direction/offset — never snap VECTOR → BLOCK_POS
Point→Block snap uses BlockSpace cell-center lattice only
  CONTAINING_CELL = pointToBlockFloor (containing cell)
  NEAREST_CENTER  = nearestCellBlockPos (round(axis - 0.5))
Distance after snap = distance to snapped cell center
Point If Grid = same lattice as world.query.is_grid_point
strict POINT_LIST / REGION_LIST / BLOCK_LIST — malformed member → Valid=false
PURE: Snap Point, Snap Point List, Point If Grid, Multi-Region
CONTEXT_READ: Selected Block / Region / Sequence / Entity (editor selection state)
Selected Block is a selection source only — compose world.read.Get Block for block data
Selected Entity Exact Position is POINT; Entity is MINECRAFT_ENTITY — compose Get Entity NBT
transient picks are NOT persisted in graph state
```

## Inventory (8)

| order | Display name | Type id | Effect |
|------:|--------------|---------|--------|
| 0 | Selected Block | `world.selection.selected_block` | `CONTEXT_READ` |
| 1 | Selected Region | `world.selection.selected_region` | `CONTEXT_READ` |
| 2 | Snap Point To Block | `world.selection.snap_point_to_block` | `PURE` |
| 3 | Snap Point List To Blocks | `world.selection.snap_points_to_blocks` | `PURE` |
| 4 | Point To Block If Grid | `world.selection.point_to_block_if_grid` | `PURE` |
| 5 | Selected Block Sequence | `world.selection.selected_block_sequence` | `CONTEXT_READ` |
| 6 | Multi-Region Selection | `world.selection.multi_region` | `PURE` |
| 7 | Selected Entity | `world.selection.selected_entity` | `CONTEXT_READ` |

Deleted in V62: `world.selection.snap_vector_to_block`.

## PURE spatial nodes

### Snap Point To Block / Snap Point List To Blocks

- Modes: `CONTAINING_CELL`, `NEAREST_CENTER` (legacy FLOOR/NEAREST remapped; CEIL → NEAREST_CENTER)
- List: strict `POINT_LIST`; no Valid Count / Skipped Count
- Outputs: Block(s), Count (list), Valid, Error, Distance (single)

### Point To Block If Grid

- Same cell-center test as Is Grid Point
- Coordinate output only when on-grid; always emits Nearest Coordinate / Distance / Offset

### Multi-Region Selection

- Inputs: `REGION_LIST`, optional Region A/B/C, Min/Max Blocks (`BLOCK_LIST`)
- Fail closed on malformed members / min-max length mismatch
- Outputs: Regions, Count, Bounds Region, Min/Max Block, Valid, Error
- No Bounds Min/Max VECTOR

## CONTEXT_READ selection sources

### Selected Block

- Outputs: Block Position, Has Selection, Valid, Error
- X/Y/Z exact INTEGER; if any connected → coordinates own source (no pick fallback)
- Compose `world.read.get_block` for BlockState / id

### Selected Region

- Outputs: Region, Min Block, Max Block, Has Selection, Valid, Error

### Selected Block Sequence

- Outputs: Blocks (`BLOCK_LIST`), Centers (`POINT_LIST`), Path (`PATH`), First/Last, Count, Is Closed, Valid, Error
- Count = unique selected blocks; closed path does not inflate Count

### Selected Entity

- Outputs: UUID, Entity Type (`ENTITY_TYPE`), Entity (`MINECRAFT_ENTITY`), Exact Position (`POINT`), Block Position, Distance, Has Entity, Valid, Error
- Block Position = containing cell of Exact Position

## Migration (V61 → V62)

- Delete Snap Vector nodes + wires
- Remap snap mode state; Multi-Region `input_min/max_points` → `input_min/max_blocks`
- Drop removed Selected*/Multi/Snap List ports
- Strip `pickedBlock` / `pickedBlocks` / `pickedEntity` / `pos1`/`pos2` from saved state

## Out of scope

- New `NodeEffect` (`INTERACTION_READ`)
- Quantize Vector / Snap Direction
- Full entity-raycast picker implementation beyond callback contract

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.WorldSelectionLanguageContractTest"
```
