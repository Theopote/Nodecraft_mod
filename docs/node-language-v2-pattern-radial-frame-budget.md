# Node Language v2 — Pattern Radial Frame & Budget (Graph V104)

**Status: ACTIVE** (Graph **V104**)

Closes Pattern Radial P1 frame continuity, P1-small singleton tangent, and P2 Polar budget after V81 language freeze:

1. Spiral / Phyllotaxis parallel-transport frames (aligned with Pattern Linear V102)
2. Count=1 tangent contract unified across layout producers
3. Polar Array `countLeavesBounded`

Related: [`node-language-v1-pattern-radial.md`](./node-language-v1-pattern-radial.md) (V43/V81 foundation; V104 runtime contracts).

## Parallel-transport frames

Spiral and Phyllotaxis emit `FRAME_LIST` via `RadialFrameUtils.placementFrames`, which delegates to `PathFrameUtils.placementFramesFromSamples`:

- First sample: `initialFrame` with WORLD_Y up hint
- Subsequent samples: parallel transport along tangents

Per-sample `initialFrame` is no longer used for multi-point layouts. This prevents roll discontinuities when tangents approach WORLD_Y and the least-aligned cardinal reference would jump.

Open paths only — no closed-loop roll correction (radial layouts are not closed loops).

## Count=1 tangent contract

Both layout producers treat Count=1 as a valid single anchor:

| Node | Tangent source |
|------|----------------|
| Spiral | Analytic derivative at index 0 |
| Phyllotaxis | Forward parametric delta `layoutPoint(1) − layoutPoint(0)` |

If the parameter-derived tangent is zero or non-finite, the node fails closed (`Valid=false`). Phyllotaxis no longer invents canonical `+X` for singleton layouts.

## Polar Array bounded leaf budget

Mirror Linear / Curve / Grid Array:

```text
sourceLeaves = countLeavesBounded(geometry, MAX_GEOMETRY_INSTANCES)
fail when sourceLeaves > max OR sourceLeaves × Count > max
```

Polar angle semantics, Include End, and full-circle seam handling are unchanged.

## Migration

V103 → V104 is a no-op format bump (runtime semantics only).
