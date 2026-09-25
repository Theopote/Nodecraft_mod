# Node Language v1 — Input Numeric

**Status: PASSED / FROZEN** (HEAD `9c4393c7`, Graph **V31**)

Language unification for `input.numeric.*` (10 nodes): finite graph-facing scalars,
UI-only precision, degrees-only angles, typed XY outputs, unified constant ports.
Shared implementation: `NumericInputUtils`.
Graph schema: **V31** retargets Pi/E to `output_value`, drops XY Slider `output_vector`,
tightens `output_uv` to `DOUBLE_LIST`, and drops incompatible downstream wires.

Related: [`node-language-v1-numeric-domain.md`](./node-language-v1-numeric-domain.md) (Domain Input / directed span),
[`node-language-v1-trigonometry.md`](./node-language-v1-trigonometry.md) (Pi/E moved here from trig),
[`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md) (finite numeric),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Finite graph outputs** — numeric source nodes never *actively* emit NaN or Infinity on
   `output_value`, `output_x` / `output_y`, `output_uv`, `output_angle`, or Domain endpoints.
   Runtime setters **and** `setNodeState()` must reject non-finite candidates (keep previous finite).
2. **Precision is UI-only** — `Precision` / `Decimal Places` affect display format only; they must
   not quantize graph doubles via `Math.round`.
3. **Slider UI ≠ graph value** — bounded sliders use normalized `t ∈ [0,1]` for the ImGui handle,
   then `lerpFromNormalized` back to double (no `(float)` cast on the stored value).
4. **Slider ranges must be usable** — min, max, and `max − min` must all be finite
   (`isFiniteUsableSpan`). Reject full IEEE span such as `[-Double.MAX_VALUE, +Double.MAX_VALUE]`.
5. **Angles are degrees** — Angle Slider and Circular Angle Picker output degrees as `DOUBLE`.
   Wrap picker angles to `[0, 360)`. No radians property or port.
6. **Scalar constants share `output_value`** — Integer Input, Number Input, Pi, and E all use
   `output_value` (not `output_pi` / `output_e`).
7. **XY Slider is not spatial** — outputs `DOUBLE` X/Y and `DOUBLE_LIST` UV only; no `VECTOR`
   port (2D parameters are not `Vector3d` geometry).

## Inventory (10)

| Display name | Type id | Class |
|--------------|---------|-------|
| Integer Input | `input.numeric.integer` | `IntegerInputNode` |
| Number Input | `input.numeric.float` | `FloatInputNode` |
| Integer Slider | `input.numeric.integer_slider` | `IntegerSliderNode` |
| Number Slider | `input.numeric.float_slider` | `FloatSliderNode` |
| Angle Slider | `input.numeric.angle` | `AngleSliderNode` |
| Circular Angle Picker | `input.numeric.angle_picker` | `CircularAngleNode` |
| XY Slider | `input.numeric.xy_slider` | `XYSliderNode` |
| Domain Input | `input.numeric.range` | `RangeInputNode` |
| Pi | `input.numeric.pi` | `PiNode` |
| E | `input.numeric.e` | `ENode` |

Display rename only (type ids unchanged): **Float Input → Number Input**,
**Float Slider → Number Slider**.

`input.values.boolean_toggle` is not part of this family (see Input Values v1).

## Integer Input (`input.numeric.integer`)

| Output | Type | Notes |
|--------|------|-------|
| `output_value` | `INTEGER` | Clamped to Min/Max; step on UI spin buttons |

Stable since Batch A (V2 Integer Slider port id migration). Not part of the V31 graph bump.

## Integer Slider (`input.numeric.integer_slider`)

| Output | Type | Notes |
|--------|------|-------|
| `output_value` | `INTEGER` | Bounded slider; port id migrated from legacy `value` (V2) |

## Number Input (`input.numeric.float`)

| Property | Graph semantics |
|----------|-----------------|
| Value | Raw `DOUBLE`; optional clamp to Min/Max |
| Min / Max | Optional bounds; `±Infinity` allowed; `NaN` rejected |
| Precision | UI format only |

Non-finite `setValue` / persisted state → keep previous finite value.

## Number Slider (`input.numeric.float_slider`)

| Property | Graph semantics |
|----------|-----------------|
| Min / Max | **Required finite** usable span |
| Current value | Raw `DOUBLE`; clamp only (no decimal rounding) |
| Decimal places | UI format only |

Slider handle: `normalizedInRange` → ImGui `t` → `lerpFromNormalized`.

## Angle Slider (`input.numeric.angle`)

| Output | Type | Unit |
|--------|------|------|
| `output_angle` | `DOUBLE` | degrees |

Finite min/max angle; runtime and state restore reject non-finite angle.

## Circular Angle Picker (`input.numeric.angle_picker`)

| Output | Type | Unit |
|--------|------|------|
| `output_angle` | `DOUBLE` | degrees in `[0, 360)` |

`setAngle`: reject non-finite **before** wrap. Precision is UI-only.

## XY Slider (`input.numeric.xy_slider`)

| Port | Type | Notes |
|------|------|-------|
| `output_x` | `DOUBLE` | Current X |
| `output_y` | `DOUBLE` | Current Y |
| `output_uv` | `DOUBLE_LIST` | Normalized `[nx, ny]` in `[0, 1]` |

Removed: `output_vector` (`VECTOR` was wrong semantic).

- Zero-width range: exact `range == 0.0` → UV component `0` (not `1e-12` epsilon).
- Optional step snap via `snapToStep` (finite quotient guard).
- Precision property: UI-only.

## Domain Input (`input.numeric.range`)

Directed domain — see [`node-language-v1-numeric-domain.md`](./node-language-v1-numeric-domain.md).

| Port | Type | Notes |
|------|------|-------|
| `output_domain` | `NUMERIC_RANGE` | Start → End, unsorted |
| `output_start` / `output_end` | `DOUBLE` | Finite endpoints only |
| `output_span` | `DOUBLE` | `safeDirectedSpan`; overflow → `NaN` |

Domain Precision is UI-only (panel input format).

## Pi / E

| Node | Port | Value |
|------|------|-------|
| `input.numeric.pi` | `output_value` | `Math.PI` |
| `input.numeric.e` | `output_value` | `Math.E` |

Legacy `output_pi` / `output_e` remapped on load (V31).

## Shared helpers (`NumericInputUtils`)

| Helper | Role |
|--------|------|
| `finiteOrFallback` | State / setter: accept finite candidate or keep fallback |
| `sanitizeOptionalBound` | Number Input min/max (`NaN` → fallback; `±Infinity` ok) |
| `sanitizeFiniteBound` | Slider axis endpoints |
| `isFiniteUsableSpan` | Slider-safe range (finite span) |
| `clampOptionalBounds` / `clampFiniteRange` | Value clamp |
| `normalizedInRange` / `lerpFromNormalized` | Double-safe slider UI |
| `wrapDegrees360` | Circular picker |
| `safeDirectedSpan` | Domain span without `-Infinity` overflow |
| `snapToStep` | XY step snap with finite quotient |

**State restore pattern:** sanitize candidate → call setter. Never assign a field from
persisted JSON then use that same field as the fallback.

## Graph migration (V30→V31)

| Action | Detail |
|--------|--------|
| Pi / E port remap | `output_pi` / `output_e` → `output_value` on connections |
| XY Slider | Drop all wires from `output_vector` |
| XY Slider UV | `output_uv` is `DOUBLE_LIST`; drop wires incompatible with declared types |

No node type-id changes. No V32 bump required for state-sanitizer fixes after V31.

## Contracts

- `InputNumericLanguageContractTest` — finite runtime + persisted state, precision UI-only,
  XY ports, Domain span, Pi/E ports, V31 migration, 10-node inventory.
- `NumericDomainLanguageContractTest` — directed domain (shared with Domain Input).
