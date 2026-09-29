# Node Language v2 — Pattern Grid Face & Budget (Graph V103)

**Status: ACTIVE** (Graph **V103**)

Closes Pattern Grid P1-small and budget P2 after V80 language freeze:

1. BOX_FACE ordered rectangular invariant in shared validator
2. Grid Array `countLeavesBounded` (aligned with V100/V102 placement/linear)

Related: [`node-language-v1-pattern-grid.md`](./node-language-v1-pattern-grid.md) (V42 foundation; V80 fail-closed extended by V103).

## BOX_FACE rectangular invariant

`BoxFaceValidator` (Graph V103) requires, in addition to V75 coplanar quad checks:

| Property | Rule |
|----------|------|
| Corners | 4 unique, finite, CCW ring order |
| Edges | Non-zero; adjacent edges perpendicular |
| Opposite edges | Parallel and equal length |
| Center | Matches average of corners |
| Normal | Aligns with edge cross product |

Consumers such as Facade Grid, Face Center Frame, Offset/Inset Box Face, and reference topology nodes all share this gate — no per-node rectangle checks.

**Fail-closed:** coplanar trapezoids, shuffled corner order (diagonal first edge), and other non-rectangular quads are rejected at validation.

Canonical faces from `BoxGeometryData` (including rotated boxes) remain valid.

## Grid Array bounded leaf budget

Mirror Linear Array / Curve Array / Place On Frames:

```text
sourceLeaves = countLeavesBounded(geometry, MAX_GEOMETRY_INSTANCES)
fail when sourceLeaves > max OR sourceLeaves × (X×Y×Z count) > max
```

Axis product preflight via `validateGridProduct` is unchanged.

## Migration

V102 → V103 is a no-op format bump (runtime semantics only).
