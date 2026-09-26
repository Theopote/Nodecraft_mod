# World Terrain — Node Language v1

**Status: PASSED / FROZEN** (Graph **V63**)

Terrain Field v1 freezes the language boundary for the 19 `world.terrain` nodes.
Algorithms are preserved; sampling, budgets, ports, and Field finite semantics are frozen.

## Spatial / Field contracts

- Raster sample point for block column `(x,z)` at sample Y `y`:
  `(x+0.5, y+0.5, z+0.5)` (Spatial Convention cell center)
- `GridScalarFieldData` index `(x,z)` stores that cell-center sample
- Continuous POINT → grid index: `BlockSpace.nearestCellIndex` / `nearestCellBlockPos`
- Outside grid domain → `NaN` (no silent CLAMP_TO_EDGE)
- Lazy field combiners may propagate NaN
- Materialize / Sample / To-Blocks / Flow Acc raster: non-finite → `Valid=false` (never coerce to 0)
- VECTOR_FIELD components must be finite at consume boundaries
- **Height Field** = normalized finite elevation in **[-1, 1]**  
  (`height=-1` → region minY, `height=+1` → region maxY in Heightfield To Blocks).  
  Final height outputs clamp to range; delta fields may be any finite signed scalar.

## Domains

| Name | X/Z | Sample Y |
|------|-----|----------|
| Local default | −32..31 | 64 |
| Continental default | ±4096 | documented per node |

- Region **unconnected** → documented default domain
- Region **connected invalid/incomplete** → `Valid=false`

## GenerationLimits (terrain)

- `MAX_TERRAIN_GRID_CELLS` = 262_144
- `MAX_TERRAIN_SIMULATION_WORK` = cells × iterations
- `MAX_TERRAIN_PLACEMENTS` / `MAX_TERRAIN_SAMPLES`
- `MAX_TERRAIN_PLATES` = 1024
- `MAX_TERRAIN_FLOW_ITERATIONS` = 4096

User budget hit → `Valid=true`, `Complete=false`, `Hit Limit=true`.  
Hard safety cap exceeded → `Valid=false`, empty outputs.

## Canonical inventory (order 0–18)

| Order | Display | Id | Notes |
|------:|---------|-----|-------|
| 0 | Height Seed Field | `world.terrain.height_seed_field` | |
| 1 | Plate Partition Field | `world.terrain.plate_partition_field` | Plate Count 2..MAX_PLATES |
| 2 | Orogenic Uplift Field | `world.terrain.orogenic_uplift_field` | |
| 3 | Rift Subsidence Field | `world.terrain.rift_subsidence_field` | |
| 4 | Combine Height Fields | `world.terrain.combine_height_fields` | |
| 5 | Flow Direction Field | `world.terrain.flow_direction_field` | |
| 6 | Flow Accumulation Field | `world.terrain.flow_accumulation_field` | auto-downsample + work cap; outside domain → NaN |
| 7 | River Mask Field | `world.terrain.river_mask_field` | |
| 8 | Precipitation Field | `world.terrain.precipitation_field` | |
| 9 | Thermal Erosion Step | `world.terrain.thermal_erosion_step` | |
| 10 | Hydraulic Erosion Step | `world.terrain.hydraulic_erosion_step` | |
| 11 | Deposition Step | `world.terrain.deposition_step` | |
| 12 | Delta Accumulate Field | `world.terrain.delta_accumulate_field` | |
| 13 | Temperature Field | `world.terrain.temperature_field` | |
| 14 | Biome Classify | `world.terrain.biome_classify` | Labels STRING_LIST |
| 15 | Heightfield To Blocks | `world.terrain.heightfield_to_blocks` | Surface Blocks; no hidden materials |
| 16 | Biome Field To Blocks | `world.terrain.biome_field_to_blocks` | BLOCK_PALETTE |
| 17 | Scalar Field Slice To Blocks | `world.terrain.scalar_field_slice_to_blocks` | |
| 18 | Sample Field On Region | `world.terrain.sample_field_on_region` | POINT_LIST + DOUBLE_LIST |

All nodes: `PURE`, `Valid` / `Error`.

### Materializers

- No hidden grass/stone/water/concrete defaults
- Surface Block required; Fill Depth > 0 ⇒ Subsurface required
- Water only when Water Level connected **and** Water Block provided
- Surface Blocks = `BLOCK_LIST` (renamed from Surface Points)
- **Max Columns** is a strict output-column budget (Fill Tiles expansion counts toward it)
- Tile end / Y span use overflow-safe arithmetic

### Sample Field On Region

- Sample Points = `POINT_LIST` (cell centers)
- Values = `DOUBLE_LIST`
- Hit Limit / Complete / Stopped Reason (Was Clamped removed)

## Migration (V62 → V63)

- `output_surface_points` → `output_surface_blocks`
- Sample: `output_points` → `output_sample_points`, `output_values` → `output_sample_values`, `output_was_clamped` → `output_hit_limit`
- Biome Classify: drop `output_legend` wires (remap to labels)
- Strip legacy comma-string palette state / hidden material default state keys

## Out of scope

- New `HEIGHT_FIELD` / `MASK_FIELD` / `CLASS_FIELD` types
- New erosion / biome algorithms
- Inventory size changes

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.WorldTerrainLanguageContractTest"
```
