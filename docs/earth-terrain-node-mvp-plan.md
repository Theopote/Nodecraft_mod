# Earth Terrain Node MVP Plan (NodeCraft)

## Goal

Design a minimum viable node set that can simulate Earth-like landforms with formation logic, not only visual noise. This plan is aligned with current NodeCraft conventions:

- Node ids: lowercase dot path, for example `world.terrain.flow_accumulation`
- Category ids: lowercase dot path, proposed `world.terrain`
- Port ids: `input_*` and `output_*`
- Types: existing `NodeDataType` only (no new datatype required for v1)

## Shared Data Contract (v1)

To keep implementation simple and compatible, v1 uses these reusable fields:

- `SCALAR_FIELD`: continuous maps (height, rain, erosion rate, sediment)
- `VECTOR_FIELD`: direction maps (flow direction, wind)
- `REGION`: simulation bounds
- `BLOCK_LIST`: sampled positions
- `LIST`: sampled numeric values aligned by index
- `INTEGER` and `DOUBLE`: scalar controls

Recommended world convention:

- Horizontal domain: X/Z
- Elevation: scalar value interpreted as Y offset
- All field nodes should be deterministic for fixed inputs and seed

## Minimum Node Set (16 nodes)

## 1) Foundation

### 1. `world.terrain.height_seed_field`
- Purpose: create initial base elevation field (continental scale)
- Inputs:
  - `input_region` (`REGION`)
  - `input_seed` (`INTEGER`)
  - `input_scale_km` (`DOUBLE`)
  - `input_continent_bias` (`DOUBLE`, 0..1)
- Outputs:
  - `output_height_field` (`SCALAR_FIELD`)
- Defaults:
  - `scale_km=180.0`, `continent_bias=0.58`

### 2. `world.terrain.sample_field_on_region`
- Purpose: sample a scalar field on regular X/Z grid
- Inputs:
  - `input_region` (`REGION`)
  - `input_field` (`SCALAR_FIELD`)
  - `input_step` (`INTEGER`)
- Outputs:
  - `output_points` (`BLOCK_LIST`)
  - `output_values` (`LIST`)
  - `output_count` (`INTEGER`)
- Defaults:
  - `step=1`

### 3. `world.terrain.combine_height_fields`
- Purpose: combine base + uplift + erosion into final height field
- Inputs:
  - `input_base_field` (`SCALAR_FIELD`)
  - `input_add_field` (`SCALAR_FIELD`)
  - `input_subtract_field` (`SCALAR_FIELD`)
  - `input_add_weight` (`DOUBLE`)
  - `input_subtract_weight` (`DOUBLE`)
- Outputs:
  - `output_height_field` (`SCALAR_FIELD`)
- Defaults:
  - `add_weight=1.0`, `subtract_weight=1.0`

## 2) Tectonics and Relief

### 4. `world.terrain.plate_partition_field`
- Purpose: pseudo plate partition (continental/oceanic + boundary strength)
- Inputs:
  - `input_region` (`REGION`)
  - `input_seed` (`INTEGER`)
  - `input_plate_count` (`INTEGER`)
- Outputs:
  - `output_plate_id_field` (`SCALAR_FIELD`)
  - `output_boundary_field` (`SCALAR_FIELD`)
- Defaults:
  - `plate_count=10`

### 5. `world.terrain.orogenic_uplift_field`
- Purpose: produce mountain uplift from plate boundary intensity
- Inputs:
  - `input_boundary_field` (`SCALAR_FIELD`)
  - `input_strength` (`DOUBLE`)
  - `input_falloff` (`DOUBLE`)
- Outputs:
  - `output_uplift_field` (`SCALAR_FIELD`)
- Defaults:
  - `strength=1.2`, `falloff=2.0`

### 6. `world.terrain.rift_subsidence_field`
- Purpose: produce rift basin and trench-like sinking zones
- Inputs:
  - `input_boundary_field` (`SCALAR_FIELD`)
  - `input_strength` (`DOUBLE`)
  - `input_width` (`DOUBLE`)
- Outputs:
  - `output_subsidence_field` (`SCALAR_FIELD`)
- Defaults:
  - `strength=0.8`, `width=1.0`

## 3) Climate and Hydrology

### 7. `world.terrain.precipitation_field`
- Purpose: latitudinal rain + rain-shadow approximation
- Inputs:
  - `input_region` (`REGION`)
  - `input_height_field` (`SCALAR_FIELD`)
  - `input_wind_field` (`VECTOR_FIELD`) optional
  - `input_equator_z` (`DOUBLE`)
  - `input_rain_base` (`DOUBLE`)
- Outputs:
  - `output_rain_field` (`SCALAR_FIELD`)
- Defaults:
  - `equator_z=0.0`, `rain_base=1.0`

### 8. `world.terrain.flow_direction_field`
- Purpose: derive steepest descent direction from height
- Inputs:
  - `input_height_field` (`SCALAR_FIELD`)
  - `input_step` (`DOUBLE`)
- Outputs:
  - `output_flow_field` (`VECTOR_FIELD`)
  - `output_slope_field` (`SCALAR_FIELD`)
- Defaults:
  - `step=1.0`

### 9. `world.terrain.flow_accumulation_field`
- Purpose: estimate drainage accumulation
- Inputs:
  - `input_region` (`REGION`)
  - `input_flow_field` (`VECTOR_FIELD`)
  - `input_rain_field` (`SCALAR_FIELD`)
  - `input_iterations` (`INTEGER`)
- Outputs:
  - `output_accumulation_field` (`SCALAR_FIELD`)
- Defaults:
  - `iterations=64`

### 10. `world.terrain.river_mask_field`
- Purpose: threshold accumulated flow to river channels
- Inputs:
  - `input_accumulation_field` (`SCALAR_FIELD`)
  - `input_threshold` (`DOUBLE`)
  - `input_min_order` (`INTEGER`)
- Outputs:
  - `output_river_mask_field` (`SCALAR_FIELD`)
- Defaults:
  - `threshold=0.62`, `min_order=2`

## 4) Erosion and Deposition

### 11. `world.terrain.thermal_erosion_step`
- Purpose: single thermal weathering step
- Inputs:
  - `input_height_field` (`SCALAR_FIELD`)
  - `input_talus` (`DOUBLE`)
  - `input_rate` (`DOUBLE`)
- Outputs:
  - `output_height_field` (`SCALAR_FIELD`)
- Defaults:
  - `talus=0.7`, `rate=0.12`

### 12. `world.terrain.hydraulic_erosion_step`
- Purpose: single hydraulic erosion step
- Inputs:
  - `input_height_field` (`SCALAR_FIELD`)
  - `input_accumulation_field` (`SCALAR_FIELD`)
  - `input_erosion_rate` (`DOUBLE`)
  - `input_capacity` (`DOUBLE`)
- Outputs:
  - `output_eroded_field` (`SCALAR_FIELD`)
  - `output_sediment_field` (`SCALAR_FIELD`)
- Defaults:
  - `erosion_rate=0.08`, `capacity=1.0`

### 13. `world.terrain.deposition_step`
- Purpose: deposit sediment in low slope / low energy areas
- Inputs:
  - `input_height_field` (`SCALAR_FIELD`)
  - `input_sediment_field` (`SCALAR_FIELD`)
  - `input_slope_field` (`SCALAR_FIELD`)
  - `input_rate` (`DOUBLE`)
- Outputs:
  - `output_height_field` (`SCALAR_FIELD`)
- Defaults:
  - `rate=0.1`

## 5) Biome and Material Mapping

### 14. `world.terrain.temperature_field`
- Purpose: temperature by latitude + elevation lapse rate
- Inputs:
  - `input_region` (`REGION`)
  - `input_height_field` (`SCALAR_FIELD`)
  - `input_equator_temp` (`DOUBLE`)
  - `input_lapse_rate` (`DOUBLE`)
- Outputs:
  - `output_temperature_field` (`SCALAR_FIELD`)
- Defaults:
  - `equator_temp=1.0`, `lapse_rate=0.18`

### 15. `world.terrain.biome_classify`
- Purpose: classify biome index from temperature and precipitation
- Inputs:
  - `input_temperature_field` (`SCALAR_FIELD`)
  - `input_precipitation_field` (`SCALAR_FIELD`)
  - `input_height_field` (`SCALAR_FIELD`)
- Outputs:
  - `output_biome_id_field` (`SCALAR_FIELD`)
- Defaults:
  - internal Whittaker-like thresholds

### 16. `world.terrain.heightfield_to_blocks`
- Purpose: convert scalar height field to block placements
- Inputs:
  - `input_region` (`REGION`)
  - `input_height_field` (`SCALAR_FIELD`)
  - `input_surface_block` (`BLOCK_TYPE`)
  - `input_subsurface_block` (`BLOCK_TYPE`)
  - `input_water_level` (`DOUBLE`)
- Outputs:
  - `output_block_placements` (`BLOCK_PLACEMENT_LIST`)
  - `output_surface_points` (`BLOCK_LIST`)

## Three Reusable Graph Recipes

### A. Continental to Mountain-River
1. `height_seed_field`
2. `plate_partition_field`
3. `orogenic_uplift_field`
4. `combine_height_fields`
5. `precipitation_field`
6. `flow_direction_field`
7. `flow_accumulation_field`
8. `river_mask_field`
9. `hydraulic_erosion_step` (loop 10-30)
10. `heightfield_to_blocks`

### B. Rift Valley + Lake Chain
1. `height_seed_field`
2. `plate_partition_field`
3. `rift_subsidence_field`
4. `combine_height_fields`
5. `flow_direction_field`
6. `flow_accumulation_field`
7. `deposition_step`
8. `heightfield_to_blocks`

### C. Arid Plateau + Canyons
1. `height_seed_field`
2. `orogenic_uplift_field`
3. `precipitation_field` (low base)
4. `flow_direction_field`
5. `flow_accumulation_field`
6. `thermal_erosion_step`
7. `hydraulic_erosion_step` (low rate)
8. `heightfield_to_blocks`

## Suggested Implementation Order

- Phase 1 (fast win, 1-2 weeks)
  - Implement nodes 1, 2, 3, 8, 9, 10, 16
  - You get mountain ridges + drainage basins + river channels
- Phase 2 (realism, 2-3 weeks)
  - Implement nodes 4, 5, 6, 11, 12, 13
- Phase 3 (ecology and lookdev, 1-2 weeks)
  - Implement nodes 7, 14, 15

## Integration Notes for Current Codebase

- Category registration
  - Add category id `world.terrain` in provider bootstrap (or rely on auto category creation)
- Annotation
  - Use `@NodeInfo(id=..., category="world.terrain", order=...)`
- Properties
  - Expose tunables with `@NodeProperty` for editor panel
- Determinism
  - All stochastic nodes must be seed-stable for same inputs
- Preview safety
  - `processNode(context)` should handle `context == null`

## v1.1 Candidate Improvements (Optional)

- Add dedicated datatypes later:
  - `HEIGHT_FIELD_GRID`
  - `RASTER_MASK`
  - `BIOME_MAP`
- Add time-step orchestrator node:
  - `world.terrain.simulation_loop`
- Add coastline and delta post-process:
  - shoreline extraction and estuary widening
