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
2. **Count 0 is empty** — connected `Count <= 0` emits no instances (no silent fallback to defaults or Spacing).
3. **Closed path does not duplicate seam** — near-closed polylines use `PathUtils.CLOSED_DISTANCE_EPSILON`; unique vertices only.
4. **Count 1 is legal** — Curve Array with `Count=1` emits one instance at path start.
5. **Typed spatial ports** — `PATH`, `POINT_LIST`, `FRAME_LIST`; no bare `LIST` for spatial data.
6. **No hidden block material** — Instance on Points requires upstream `BLOCK_PLACEMENT_LIST`; no `minecraft:stone` fallback.
7. **No deconstruct outputs** — Instance on Points drops Positions / Block IDs mirrors.
8. **Index alignment** — Curve Array `copies`, `frames`, and `origins` share the same length and index.
9. **No legacy block-array nodes** — old BLOCK_LIST linear array and Along Path nodes are removed (not hidden).

## Inventory (4)

| Display name | Type id | Role |
|--------------|---------|------|
| Linear Array | `pattern.linear.linear_array` | Geometry copies along a direction vector |
| Path Frames | `pattern.linear.path_frames` | PATH -> FRAME_LIST + POINT_LIST |
| Instance on Points | `pattern.linear.instance_on_points` | BLOCK_PLACEMENT_LIST + POINT_LIST -> instanced placements |
| Curve Array | `pattern.linear.curve_array` | Convenience geometry copies along a path |

## Canonical workflows

**Geometry linear array**

```
Geometry -> Linear Array -> Voxelize Geometry -> Assign Block Type
```

**Point instancing**

```
Assign Block Type -> BLOCK_PLACEMENT_LIST -> Instance on Points
POINT_LIST ---------------------------------^
```

**Curve array (convenience)**

```
Geometry + Path -> Curve Array -> Voxelize Geometry -> Assign Block Type
```

Equivalent explicit chain: `Path -> Path Frames -> Place Geometry On Frames`.

## Staggered Grid

Staggered grid layout lives under **`pattern.grid.staggered_grid`** (see
[`node-language-v1-pattern-grid.md`](./node-language-v1-pattern-grid.md)).
It is no longer part of `pattern.linear`.

## Closed path helpers

`PathUtils.closedUniqueVertices(samples)` returns unique vertices with the same
`tolerance` as `PathUtils.isClosed()`. All path-frame and curve-array nodes use this
helper — do not reimplement seam trimming locally.

## Geometry output shape

- `0` copies -> `output_geometry = null`
- `1` copy -> original geometry value (not wrapped in Composite)
- `2+` copies -> `CompositeGeometryData`

## Count vs Spacing (Curve Array)

- **Count not connected** — use Spacing when `Spacing > 0`.
- **Count connected and `>= 1`** — Count mode; Spacing ignored.
- **Count connected and `<= 0`** — empty output; do not fall back to Spacing.

## Graph format

Pattern Linear v1 ships at **Graph V41**. No dedicated V40->V41 migration — development
builds use current node ids and ports directly.

## Contracts

- `PatternLinearLanguageContractTest` — inventory, Count semantics (including Count=0),
  closed/near-closed seam, Staggered Grid output cap, Count=1, typed ports, no hidden stone,
  index alignment.
- `PatternArrayFamilyContractTest` — shared array/frame integration checks.
