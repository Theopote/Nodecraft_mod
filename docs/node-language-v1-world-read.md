# Node Language v1 — World Read

**Status: PASSED / FROZEN** (Graph **V61**)

Discrete Minecraft block/entity NBT and region scanners under `world.read` (11 nodes).

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`node-language-v1-world-query.md`](./node-language-v1-world-query.md) (V60),
Spatial Convention v1 (`BlockSpace` cell-center lattice).

## Product boundary

```text
strict BLOCK_POS only — no Vector/POINT floor coercion into block reads
user budget vs hard safety cap (fail closed, never clamp above hard cap)
OptionalPortDrive / connection-aware optionals
typed collections: BLOCK_INFO_LIST, STRING_LIST, INTEGER_LIST, BLOCK_LIST
Valid + Error; scanners also Complete / Hit Limit / Stopped Reason
Get Entity NBT is entity-object only — compose with world.query.Get Entity
Get Block Positions In Region is PURE (no world access)
no Biome At Player (compose Player Position → Get Biome)
Heightmap / Surface use Region XZ footprint only
```

## Hard caps (fail closed)

| Cap | Value | Used by |
|-----|------:|---------|
| `MAX_WORLD_READ_BLOCKS` | 262_144 | region block scans, block-position budgets |
| `MAX_WORLD_READ_COLUMNS` | 65_536 | Heightmap / Surface column budgets |
| `MAX_WORLD_READ_RESULTS` | 262_144 | Find Blocks Max Results |

Requested exact INTEGER above the hard cap → `Valid=false`. Unconnected budgets use documented defaults ≤ hard cap.

## Inventory (11)

| order | Display name | Type id | Effect |
|------:|--------------|---------|--------|
| 0 | Get Block | `world.read.get_block` | `WORLD_READ` |
| 1 | Get Blocks In Region | `world.read.get_blocks_in_region` | `WORLD_READ` |
| 2 | Find Blocks | `world.read.find_blocks` | `WORLD_READ` |
| 3 | Get Biome | `world.read.get_biome` | `WORLD_READ` |
| 4 | Get Block Positions In Region | `world.read.get_block_positions_in_region` | `PURE` |
| 5 | Get Heightmap | `world.read.get_heightmap` | `WORLD_READ` |
| 6 | Get Surface Blocks | `world.read.get_surface_blocks` | `WORLD_READ` |
| 7 | Scan Region By Type | `world.read.scan_region_by_type` | `WORLD_READ` |
| 8 | Get Block NBT | `world.read.get_block_nbt` | `WORLD_READ` |
| 9 | Get Entity NBT | `world.read.get_entity_nbt` | `WORLD_READ` |
| 10 | Read Sign Text | `world.read.read_sign_text` | `WORLD_READ` |

Deleted in V61: `world.read.biome_at_player`. Renamed: `get_points_in_region` → `get_block_positions_in_region`.

## Point / single-block readers

### Get Block

- Input: `Coordinate` (`BLOCK_POS`)
- Outputs: block `BLOCK_INFO` (+ derived fields), `Valid`, `Error`

### Get Biome

- Input: `Coordinate` (`BLOCK_POS`)
- Outputs: `Biome`, `Biome Name`, real `Temperature`, `Valid`, `Error`
- Removed V61: Looks Ocean / Estimated Downfall heuristics

### Get Block NBT

- Inputs: `Coordinate` (`BLOCK_POS`), `Max String Length` (exact INTEGER 1..65536; unconnected → 4096)
- No block entity → `Valid=true`, `Has Block Entity=false`, NBT null (not an invalid query)
- Removed V61: `Success` (use `Valid`)

### Get Entity NBT

- Inputs: `Entity` (`MINECRAFT_ENTITY`) + `Max String Length` only
- Compose lookup via `world.query.get_entity`
- Removed V61: UUID / Entity Type / Find Nearest / Max Distance / Distance

### Read Sign Text

- Input: `Coordinate` (`BLOCK_POS`)
- Outputs: `Text Lines` (`STRING_LIST`), Combined Text, Is Sign, Sign Type, `Valid`, `Error`
- Removed V61: Include Formatting; Success (use `Valid`)

## Region scanners

Shared outputs: `Valid`, `Error`, `Complete`, `Hit Limit`, `Stopped Reason`. Bounded scans may return legal partial results (`Valid=true`, `Hit Limit=true`, `Complete=false`).

### Get Blocks In Region

- `Blocks List` → `BLOCK_INFO_LIST`; Exclude Air via OptionalPortDrive

### Find Blocks

- Connection-aware target: Block Info connected owns mode; else Target Block Type
- Max Results ≤ `MAX_WORLD_READ_RESULTS`

### Get Heightmap / Get Surface Blocks

- Region **XZ footprint only**; Height Values → `INTEGER_LIST`; Surface Blocks → `BLOCK_INFO_LIST`; Block Types → `STRING_LIST`

### Scan Region By Type

- Target → `BLOCK_TYPE`
- Keep `Block Type IDs` (`STRING_LIST`) + `Counts` (`INTEGER_LIST`)
- Removed V61: `Entries`, display-string `Type Counts`

## Pure enumeration

### Get Block Positions In Region

- Effect: `PURE`
- Strict `REGION`; optional Coordinates via strict `BLOCK_LIST`
- Max Points exact INTEGER ≤ `MAX_WORLD_READ_BLOCKS`
- Under budget → full enum + `Complete=true`; over budget → uniform deterministic sample + `Sampled=true`, `Complete=false`
- Filter mode: truncate with Hit Limit (not resample)

## Migration (V60 → V61)

- Rename type id `world.read.get_points_in_region` → `world.read.get_block_positions_in_region`
- Drop Biome At Player nodes + incident wires
- Drop removed ports listed above; remap Block NBT / Sign `output_success` → `output_valid`

## Out of scope

- New `BLOCK_STATE_DATA` type / replacing raw `BlockState` in `BLOCK_INFO`
- Sign FRONT/BACK sides
- NBT extraction memory budget beyond SNBT string length
- Changes to frozen `world.query` V60 beyond composing Get Entity → Get Entity NBT

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.WorldReadLanguageContractTest"
```
