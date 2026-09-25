# Node Language v1 — Numeric Domain & Sampling

## Directed Domain (NUMERIC_RANGE)

A **Domain** is a directed interval **Start → End**, not unordered bounds.

- `Domain(0, 10)` and `Domain(10, 0)` are different.
- **Remap** and **Graph Mapper** use direction (`delta`, `lerp`).
- **Clamp** and **Random** use `lower()` / `upper()` only.

### Domain Input

- Outputs: `Domain`, `Start`, `End`, `Span` (directed delta).
- Does **not** force-sort Start/End.
- Runtime and persisted state reject non-finite Start/End.
- `output_span` uses `NumericInputUtils.safeDirectedSpan` (overflow → `NaN`, not `-Infinity`).

See [`node-language-v1-input-numeric.md`](./node-language-v1-input-numeric.md) for full Input Numeric v1 rules.

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
| Number Input | Precise value; optional Min/Max (`input.numeric.float`) |
| Number Slider | Bounded exploration; finite Min/Max required (`input.numeric.float_slider`) |
| Angle Slider / Circular Angle Picker | Degrees only; use Degrees To Radians for rad |

Full inventory and freeze: [`node-language-v1-input-numeric.md`](./node-language-v1-input-numeric.md).

## Property = Default, Port = Override

Unconnected port → property default applies.
