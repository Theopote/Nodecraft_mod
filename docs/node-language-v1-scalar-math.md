# Node Language v1 — Scalar Math

Freeze for `math.scalar_math.*` continuous numeric nodes.
Shared implementation: `com.nodecraft.nodesystem.math.ScalarMathOps`.
Graph schema: **V25** drops deleted Fraction / Graph Mapper ports (see migration below).

Related: [`node-language-v1-numeric-domain.md`](./node-language-v1-numeric-domain.md) (directed Domain),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md) (DOUBLE language).

## Finite-result contract

For every **continuous** scalar node (including Graph Mapper and Expression):

| State | Numeric outputs | Valid |
|-------|-----------------|-------|
| Success | All `Double.isFinite` | `true` |
| Failure | All `Double.NaN` | `false` |

Invariant: **`Valid=true` ⇒ every numeric output is finite.**

Forbidden:

- `Infinity` / `-Infinity` with `Valid=false`
- `Infinity` / `-Infinity` with `Valid=true`

Exception (integer domain only): **Integer Divide** keeps `0` + `Valid=false` on failure.

## Zero / degenerate semantics

No per-node magic epsilon for graph-facing zero/degeneracy.

| Scenario | Rule |
|----------|------|
| Division / Modulus divisor | Invalid only when `b == 0.0d`; then non-finite result → NaN+false |
| Remap / Graph Mapper source domain | `source.delta() == 0.0d` → invalid |
| Smoothstep edges | `edge0 == edge1` → invalid |
| Logarithm base | `base == 1.0d` (or base ≤ 0 / number ≤ 0) → invalid |

Expression `/`, `%`, and `smoothstep` use the **same** rules via `ScalarMathOps`.

Local solver tolerances (e.g. Graph Mapper Bezier Newton) are internal only — not division semantics.

## Round semantics

| Node | Ports | Rule |
|------|-------|------|
| Round | `DOUBLE → DOUBLE` | Nearest integer-valued double, **ties-to-even** (`Math.rint`) |
| Floor / Ceiling | `DOUBLE → DOUBLE` | `Math.floor` / `Math.ceil` |

Do **not** cast through `long` (`Math.round(double)`). Expression `round` matches Round.

## Node inventory (23)

### Basic arithmetic

Addition, Subtraction, Multiplication, Division, Modulus, Power

### Numeric functions

Absolute, Square Root, Logarithm, Min, Max, Sign, Fraction

### Quantization

Floor, Ceiling, Round, Integer Divide

### Range / mapping

Clamp, Remap, Lerp, Smoothstep

### Advanced

| Node | Role |
|------|------|
| Graph Mapper | Advanced visual curve mapper — fixed ports Value/Source/Target → Result/T/Mapped/Valid; curve params are properties |
| Expression | Advanced escape hatch — not a peer of Addition/Multiply for beginner workflows |

## KEEP decisions

- **Lerp** extrapolates for `T` outside `[0,1]` — no Clamp T property; compose `Clamp → Lerp` if needed
- **Clamp / Remap** use `NUMERIC_RANGE` (directed Domain)
- **Integer Divide** is distinct from Division (`floorDiv` / `floorMod`)
- Do not mass-delete nodes; do not remove `Valid` ports in Scalar v1

## Fraction

Single action: `frac(x) = x - floor(x)`. Outputs: Frac + Valid only (no Floor echo port).
Need Floor? Use the Floor node.

## Graph migration (V24→V25)

Scalar Math v1 schema cleanup — drop obsolete wires only (no value guessing):

| Node | Removed port | Side |
|------|--------------|------|
| `math.scalar_math.frac` | `output_floor` | source |
| `math.scalar_math.graph_mapper` | `input_exponent` | target |
| `math.scalar_math.graph_mapper` | `input_gaussian_center` | target |
| `math.scalar_math.graph_mapper` | `input_gaussian_width` | target |

Curve parameters on Graph Mapper remain `@NodeProperty` only.
