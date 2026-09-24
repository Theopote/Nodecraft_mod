# Node Language v1 — Numeric Domain & Sampling

## Directed Domain (NUMERIC_RANGE)

A **Domain** is a directed interval **Start → End**, not unordered bounds.

- `Domain(0, 10)` and `Domain(10, 0)` are different.
- **Remap** and **Graph Mapper** use direction (`delta`, `lerp`).
- **Clamp** and **Random** use `lower()` / `upper()` only.

### Domain Input

- Outputs: `Domain`, `Start`, `End`, `Span` (directed delta).
- Does **not** force-sort Start/End.

### Number Sequence vs Domain

| Concept | Node | Output |
|---------|------|--------|
| Continuous domain | Domain Input | NUMERIC_RANGE |
| Discrete sequence | Number Sequence | LIST |

## Discrete Count Language (INTEGER ports)

| Name | Meaning |
|------|---------|
| Count | Final instance/object count |
| Segments | Topology segment count (Circle) |
| Samples | Sample point count (Arc, Bezier) |
| Steps | Process discrete steps |
| Iterations | Algorithm repeat count |
| Resolution | Only when none of the above apply |

## Path Sampling

Explicit **Count** or **Spacing** arc-length sampling lives on **Resample Path** only.
See `docs/node-language-v1-curve-path.md` for curve/path node roles.

| Node | Role |
|------|------|
| Extract Path Points | Extract existing vertices (no resample) |
| Resample Path | Resample by Count or Spacing → PATH |
| Path Frames | Frames at path vertices (sole frame producer) |
| Evaluate Path | Point + tangent at normalized t ∈ [0..1] |

## Input node semantics

| Node | Purpose |
|------|---------|
| Float Input | Precise value; optional Min/Max |
| Float Slider | Bounded exploration; Min/Max required |
| Angle Slider / Circular Angle Picker | Degrees only; use Degrees To Radians for rad |

## Property = Default, Port = Override

Unconnected port → property default applies.
