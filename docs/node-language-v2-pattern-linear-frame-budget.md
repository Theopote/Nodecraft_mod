# Node Language v2 — Pattern Linear Frame & Budget (Graph V102)

**Status: ACTIVE** (Graph **V102**)

Closes Pattern Linear P1s and budget P2s after V79 language freeze:

1. Connected Up Vector ∥ path tangent → fail-closed
2. Closed-path parallel-transport roll (holonomy) correction
3. Linear / Curve Array use `countLeavesBounded`
4. Instance Block Placements product preflight before allocate

Related: [`node-language-v1-pattern-linear.md`](./node-language-v1-pattern-linear.md) (V41 / V79 inventory).

## Up Vector contract

| Port state | Behavior |
|------------|----------|
| Unconnected | Tolerant `initialFrame` — stable cardinal when world-Y ∥ tangent |
| Connected + finite non-zero + usable vs first tangent | Must use that Up |
| Connected + zero / invalid | `Valid=false` (unchanged V79) |
| Connected + parallel to first tangent | `Valid=false` — no `leastAlignedCardinal` silent repair |

Implemented via `PathFrameUtils.initialFrameRequireUp` and `placementFramesFromSamples(..., requireUp, closed)` for Path Frames and Curve Array (`orientToPath`).

## Closed-path frame seam

Position seam dedupe (no duplicate A at A…A') remains. For closed paths with ≥3 samples, after open parallel transport:

1. Transport last frame onto first origin/tangent
2. Measure section roll error about the first tangent
3. Distribute `-error * (arcLength[i] / totalArc)` as section rotation (frame 0 fixed)

So first/last neighbors agree in orientation (building loops / Curve Array).

Open paths unchanged. Other `PathFrameUtils` callers keep the 3-arg open overload unless they pass `closed=true`.

## Budgets

- **Linear Array / Curve Array:** `GeometryStructureUtils.countLeavesBounded(geometry, MAX_GEOMETRY_INSTANCES)` then `leaves × instances` — same idiom as Place Geometry On Frames (V100).
- **Instance Block Placements:** raw `Collection` size product vs `MAX_LIST_ELEMENTS` before resolve; `BlockListUtils.resolveStrictBlockListBounded`; template size gated before copy.

## Migration

V101 → V102 is a no-op format bump (runtime semantics only).
