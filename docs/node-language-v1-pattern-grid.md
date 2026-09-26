# Node Language v1 — Pattern Grid

**Status: PASSED / FROZEN** (Graph **V42**)

Language unification for the five canonical `pattern.grid.*` nodes: one geometry-first
grid array plus four continuous layout producers — aligned with Pattern Linear v1 Count
semantics, typed spatial ports, and geometry-first workflows.

Related: [`node-language-v1-pattern-linear.md`](./node-language-v1-pattern-linear.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Count = total emitted instances** — array and layout nodes emit exactly the product of axis counts.
2. **Count 0 is empty** — any axis Count <= 0 yields empty output and Valid=false.
3. **INTEGER ports accept Integer only** — no silent Number truncation (e.g. 3.8 -> 3).
4. **Layout producers emit POINT_LIST** — no BLOCK_LIST anchor copying inside grid layout nodes.
5. **No bare spatial LIST** — use POINT_LIST, PATH_LIST, BOOLEAN_LIST as appropriate.
6. **Grid Array is the sole geometry-copy node** — other grid nodes are layout producers.
7. **Geometry output shape (Grid Array)** — 0 -> null, 1 -> raw Geometry, 2+ -> CompositeGeometryData.
8. **Fail closed on invalid input** — zero connected direction vectors, invalid Facade margins/rows fail Valid=false.

## Inventory (5)

| Display name | Type id | Role |
|--------------|---------|------|
| Grid Array | `pattern.grid.grid_array` | Geometry copies on X/Y/Z grid |
| Facade Grid | `pattern.grid.facade_grid` | BOX_FACE -> cell centers + boundaries |
| Staggered Grid | `pattern.grid.staggered_grid` | Staggered anchor layout |
| Hex Grid | `pattern.grid.hex_grid` | Hexagonal lattice anchors (X/Z plane) |
| Triangular Grid | `pattern.grid.triangular_grid` | Triangular lattice anchors + orientation |

## Node categories

### A. Instance / Array

**Grid Array** — the only node that copies Geometry directly.

```
Geometry -> Grid Array -> Voxelize Geometry -> Assign Block Type
```

Inputs: Geometry, X/Y/Z Direction + Distance + Count.
Outputs: Geometry, Geometries, Offsets, Geometry Tree, Offset Tree, Count, Valid.

### B. Layout / Grid producers

**Facade Grid**, **Staggered Grid**, **Hex Grid**, **Triangular Grid** emit continuous
anchor points (and Facade cell boundaries as PATH_LIST). Downstream:

```
Layout Producer -> Instance on Points
Layout Producer -> Place Geometry on Frames
```

**Facade Grid** scope (v1): rectangular subdivision of a **BOX_FACE** only.
Outputs: Center Points (POINT_LIST), Cell Boundaries (PATH_LIST), Cell Width/Height, Cell Count, Valid.
No Center Blocks mirror output.

**Staggered Grid** — row/column parity layout with optional alternate row height.

**Hex Grid** — Q Count x R Count hex lattice on the X/Z plane (Origin + Radius).

**Triangular Grid** — U Count x V Count triangular lattice; Triangle Up (BOOLEAN_LIST).

## Count semantics

All Count inputs are **total positions along that axis**, using loops `0 .. count-1`:

- Grid Array 3 x 5 x 1 -> 15 geometry copies
- Staggered 5 steps x 3 rows -> 15 points
- Hex Q=4, R=4 -> 16 points (not 81)
- Triangular U=8, V=8 -> 64 points (not 289)

Total output capped via `GenerationLimits.clampExclusiveGridCounts` / `clampExclusiveGeometryGridCounts`.

## Removed legacy

- BLOCK_LIST `pattern.grid.grid_array` (old coordinate repeater)
- `pattern.grid.grid_array_geometry` (merged into canonical Grid Array)
- `pattern.grid.triangle_grid` (renamed to `triangular_grid`)
- Include Original on Grid Array
- Facade Grid Center Blocks output

## Graph format

Pattern Grid v1 ships at **Graph V42**. No dedicated V41->V42 migration — development
builds use current node ids and ports directly.

## Contracts

- `PatternGridLanguageContractTest` — inventory, Count semantics, typed ports, output caps, fail-closed validation.
- `PatternLinearLanguageContractTest` — linear pattern nodes (Graph V41 freeze identity retained).

## Deferred (P2)

- Hex / Triangular arbitrary plane via Frame or U/V vectors
- Facade Grid beyond BOX_FACE (future Surface Grid)
