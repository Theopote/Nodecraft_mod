# Node Language v1 — Pattern Linear

**Status: PASSED / FROZEN** (Graph **V41**)

Language unification for the four canonical `pattern.linear.*` nodes: geometry-first
linear/curve arrays, path frames, and typed point instancing — no hidden material
defaults, no bare spatial LIST ports, and closed-path seam rules aligned with polar
full-circle semantics.

Related: [`node-language-v1-basic-assignment.md`](./node-language-v1-basic-assignment.md),
[`node-language-v1-curve-path.md`](./node-language-v1-curve-path.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Count = total emitted instances** — array nodes emit exactly `Count` copies (not repeat+original).
2. **Closed path does not duplicate seam** — polyline `A-B-C-A` yields frames/instances at `A, B, C` only.
3. **Count 1 is legal** — Curve Array with `Count=1` emits one instance at path start.
4. **Typed spatial ports** — `PATH`, `POINT_LIST`, `FRAME_LIST`; no bare `LIST` for spatial data.
5. **No hidden block material** — Instance on Points requires upstream `BLOCK_PLACEMENT_LIST`; no `minecraft:stone` fallback.
6. **No deconstruct outputs** — Instance on Points drops Positions / Block IDs mirrors.
7. **Index alignment** — Curve Array `copies`, `frames`, and `origins` share the same length and index.
8. **Legacy block-array nodes hidden** — `linear_array` and `along_path` are `@Deprecated` and not registered.

## Inventory (4)

| Display name | Type id | Role |
|--------------|---------|------|
| Linear Array | `pattern.linear.linear_array_geometry` | Geometry copies along a direction vector |
| Path Frames | `pattern.linear.path_instances` | PATH → FRAME_LIST + POINT_LIST |
| Instance on Points | `pattern.linear.instance_on_points` | BLOCK_PLACEMENT_LIST + POINT_LIST → instanced placements |
| Curve Array | `pattern.linear.curve_array_geometry` | Convenience geometry copies along a path |

## Canonical workflows

**Geometry linear array**

```
Geometry → Linear Array → Voxelize Geometry → Assign Block Type
```

**Point instancing**

```
Assign Block Type → BLOCK_PLACEMENT_LIST → Instance on Points
POINT_LIST ────────────────────────────────┘
```

**Curve array (convenience)**

```
Geometry + Path → Curve Array → Voxelize Geometry → Assign Block Type
```

Equivalent explicit chain: `Path → Path Frames → Place Geometry On Frames`.

## Staggered Grid

Two-dimensional staggered block coordinates live under **`pattern.grid.staggered_grid`**
(not `pattern.linear`). Row Count and Step Count are total emitted rows/steps per row.

## Geometry output shape

- `0` copies → `output_geometry = null`
- `1` copy → original geometry value (not wrapped in Composite)
- `2+` copies → `CompositeGeometryData`

## Graph migration (V40→V41)

| Action | Detail |
|--------|--------|
| Drop wires | Instance on Points `output_positions`, `output_block_ids`, `output_count` |
| Drop wires | Instance on Points `input_template_coordinates`, `input_block_info` |

No automatic remap for deprecated `linear_array`, `along_path`, or renamed `staggered_array`.

## Contracts

- `PatternLinearLanguageContractTest` — inventory, Count semantics, closed seam, Count=1,
  typed ports, no hidden stone, index alignment, V40→V41 migration.
- `PatternArrayFamilyContractTest` — shared array/frame integration checks.
