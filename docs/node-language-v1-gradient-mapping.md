# Node Language v1 — Gradient Mapping

**Status: PASSED / FROZEN** (Graph **V37**)

Language unification for exactly **5** `material.gradient_mapping.*` nodes: PURE
scalar→normalize→`BLOCK_PALETTE`→`blockId`-only remapping, without mutating
`stateData` or fabricating hidden vanilla materials / origin / epsilon domains.

Shared helper: `GradientMaterialUtils` + `MaterialMappingSupport`. Noise kernel:
`RandomOps.valueNoise3`. Graph schema: **V37** drops deconstruct outputs, tightens
diagnostics to `DOUBLE_LIST`, Distance reference to `POINT`, and strips legacy
`rampBlocks` property state.

Related: [`node-language-v1-directional-mapping.md`](./node-language-v1-directional-mapping.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **PURE** — all five nodes.
2. **blockId only** — `pos` and `stateData` unchanged; only `blockId` may change.
3. **Preserve on partial override** — placement source + missing mapped material/palette
   entry → keep `source.blockId` (never invent `minecraft:stone`).
4. **Geometry / coordinates path** — explicit band material or palette (or fallback
   voxelization base where applicable) required; otherwise `Valid=false`, `[]`.
5. **No deconstruct duplicate outputs** — canonical output is `BLOCK_PLACEMENT_LIST`
   plus `Valid` / `Error` (diagnostics where listed are `DOUBLE_LIST`).
6. **Fail-closed domains** — non-finite params, zero-width ranges, missing SDF,
   `halfWidth ≤ 0`, missing/ambiguous Distance reference → `Valid=false`.

## Inventory (5)

| Display name | Type id | Scalar |
|--------------|---------|--------|
| Height Gradient Map | `material.gradient_mapping.height_gradient_map` | relative Y bands |
| Noise Material | `material.gradient_mapping.noise_material` | `RandomOps.valueNoise3` |
| Height Palette Map | `material.gradient_mapping.gradient_ramp_map` | equal-height palette bins |
| Distance-Based Material | `material.gradient_mapping.distance_material` | geometric distance |
| SDF-Driven Material | `material.gradient_mapping.sdf_material` | signed distance |

Display rename only for Gradient Ramp → **Height Palette Map** (type id unchanged).

## Height Gradient Map

Relative Y bands Bottom / Middle / Top / Peak via finite ratios
`0 ≤ lower ≤ middle ≤ upper ≤ 1`. Single height (`maxY == minY`) → `t = 0` → Bottom.
Unconnected band ports preserve `source.blockId`.

## Noise Material

Octave stack of `RandomOps.valueNoise3`. Seed = Integer-only via
`RandomOps.resolveSeed`. `scale > 0`; thresholds finite with `low < high`.
`output_noise_values : DOUBLE_LIST`.

## Height Palette Map

Equal bins from `input_palette : BLOCK_PALETTE`. Empty palette + placements → preserve.
Single height → `t = 0` (first entry / preserve). No legacy `rampBlocks` string property.

## Distance-Based Material

Exactly one of Point / Plane / Curve / Polyline / Line. Reference point port is
`POINT` (VECTOR rejected). Domain `min < max` finite. Degenerate curve/polyline
(`<2` points) → invalid. `output_distances : DOUBLE_LIST`.

## SDF-Driven Material

Missing SDF → `Valid=false`. Half width finite and `> 0`. Non-finite samples → batch
invalid. `output_distances` / `output_weights : DOUBLE_LIST`.

## Graph migration (V36→V37)

| Action | Detail |
|--------|--------|
| Drop wires | `output_positions`, `output_block_ids` from all five gradient_mapping nodes |
| Type tighten | Noise/Distance/SDF diagnostic `LIST` → `DOUBLE_LIST` (drop incompatible) |
| Port remap | Distance `input_reference_point` VECTOR→POINT (drop incompatible) |
| Ramp state | Drop legacy `rampBlocks` property from saved state (no auto-insert palette) |

## Contracts

- `GradientMappingLanguageContractTest` — inventory, PURE, Valid gates, RandomOps kernel,
  preserve/no-stone, Distance exactly-one / POINT, SDF fail-closed, V36→V37 migration.
- `MaterialFamilyContractTest` — height remap preserve.
- Format fences bumped to **V37**.
