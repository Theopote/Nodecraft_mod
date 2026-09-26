# Node Language v1 — Reference Points

**Status: PASSED / FROZEN** (Graph **V49**)

Language unification for the nineteen canonical `reference.points.*` nodes: strict INTEGER
semantics, strict POINT_LIST fail-closed, unified `SpatialValueResolver`, typed box topology
outputs (`LINE_LIST` / `INTEGER_LIST`), Get Box Face precedence, and unique node ordering 0–18.

Related: [`node-language-v1-reference-frames.md`](./node-language-v1-reference-frames.md),
[`node-language-v1-reference-planes.md`](./node-language-v1-reference-planes.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## POINT vs BLOCK_POS

```text
BLOCK_POS = integer block grid cell (Construct Block Position, Block Position Input)
POINT     = continuous geometric position (Construct Point, Block To Point explicit conversion)
```

Continuous math nodes emit `POINT` only. `BLOCK_POS` requires explicit integer construction
or block-grid input nodes — no silent truncation from doubles.

## Strict INTEGER

Runtime INTEGER ports accept exact `Integer` only (no `Number.intValue()` truncation).

- Block Position Input: connected non-Integer on X/Y/Z -> `Valid=false`, `Block Pos=null`
- Construct Block Position: any non-Integer component -> `Valid=false`
- Get Box Corner / Face / Face Edge index ports: connected non-Integer -> `Found=false`

Deconstruct Block Position keeps `0` sentinel on invalid inputs (INTEGER has no NaN).

### Connected vs unconnected optional inputs

Optional override ports use **port connection state**, not `inputValues != null`:

```text
unconnected              -> property fallback
connected + valid input  -> input override
connected + null/invalid -> fail closed (no property fallback)
```

Applies to Block Position Input X/Y/Z and Get Box Face Face Name / Face Index selection.

## Strict POINT_LIST

`PointUtils.resolveStrictPointList()` shared by Closest Point, Point List Center, Point List Bounds:

```text
null / not Collection / empty -> invalid
any non-PointData element -> invalid (whole node)
any non-finite position -> invalid (whole node)
else -> full list (no filtering)
```

Count = input list size when valid; invalid -> `Count=0`, `Valid=false`.

Closest Point invalid -> `Index=-1`, `Distance=NaN`.

## Shared PointUtils

Single helper class: `com.nodecraft.nodesystem.util.PointUtils`

Public API:
- `EPS`, `isFinite`, `distanceSquared`
- `toPointPosition()` — strict `PointData` only on typed POINT ports
- `resolveStrictPointList()`

Point/vector resolution at boundaries uses `SpatialValueResolver` (not duplicated in PointUtils).

## Inventory (19)

| order | Display name | Type id | Role |
|------:|--------------|---------|------|
| 0 | Block Position Input | `reference.points.block_position` | Integer block position source |
| 1 | Construct Block Position | `reference.points.construct_coordinate` | X/Y/Z integers -> BLOCK_POS |
| 2 | Deconstruct Block Position | `reference.points.deconstruct_block_position` | BLOCK_POS -> integers |
| 3 | Block To Point | `reference.points.point_from_block` | Explicit BLOCK_POS -> POINT |
| 4 | Construct Point | `reference.points.construct_point` | X/Y/Z doubles -> POINT |
| 5 | Deconstruct Point | `reference.points.deconstruct_point` | POINT -> doubles |
| 6 | Translate Point | `reference.points.translate_point` | Point + Vector displacement |
| 7 | Move Point Along Direction | `reference.points.point_along_vector` | Point + normalized direction |
| 8 | Mid Point | `reference.points.mid_point` | Average of two points |
| 9 | Distance Between Points | `reference.points.distance_between_points` | Euclidean distance |
| 10 | Vector Between Points | `reference.points.vector_between_points` | To - From displacement |
| 11 | Closest Point | `reference.points.closest_point` | Nearest in POINT_LIST |
| 12 | Point List Center | `reference.points.point_list_center` | Centroid of POINT_LIST |
| 13 | Point List Bounds | `reference.points.point_list_bounds` | AABB min/max of POINT_LIST |
| 14 | Get Box Corner | `reference.points.get_box_corner` | Corner by index |
| 15 | Get Box Face | `reference.points.get_box_face` | Face by name or index |
| 16 | Get Face Edge | `reference.points.get_face_edge` | Edge by index |
| 17 | Deconstruct Box Face | `reference.points.deconstruct_face` | Full face breakdown |
| 18 | Deconstruct Face Edge | `reference.points.deconstruct_edge` | Line -> endpoints + direction |

## Get Box Face precedence

```text
1. Face Name connected -> resolve by name only
   connected but unrecognized -> Found=false (no property/index fallback)
2. else Face Index connected -> strict Integer index path
3. else defaultFaceName property -> fallback
4. else Found=false
```

Index handling on Get Box Corner / Face / Face Edge: negative -> wrap -> bounds
(when properties enabled).

## Invalid numeric sentinels

Aligned with signed-distance / plane query patterns:

- DOUBLE invalid outputs use `NaN` where `(0,0,0)` would be ambiguous (Deconstruct Point,
  Distance, Vector Between Points, Closest Point, Deconstruct Face Edge length)
- INTEGER invalid outputs use `0` or `-1` where NaN is unavailable

## Typed topology (Deconstruct Box Face)

| Output | Type |
|--------|------|
| Edges | `LINE_LIST` |
| Corner Indices | `INTEGER_LIST` |
| Valid | `BOOLEAN` |

Removed at V49: `Edge Corner Index Pairs` (no port alias migration).

Deconstruct Face Edge rejects degenerate lines (`lengthSquared <= EPS`).

## Deferred (P2 — non-blocking)

- `PointData.canonical()` / constructor finite invariant
- `LineData` finite/non-degenerate invariant at datatype layer
- Rename display/id `mid_point` -> `midpoint` (optional canonical naming)
- Further slim Deconstruct Box Face outputs (e.g. drop Plane)
