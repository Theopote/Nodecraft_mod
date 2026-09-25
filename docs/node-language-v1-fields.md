# Node Language v1 — Fields

**Status: PASSED / FROZEN** (HEAD `540daeef`, Graph **V30**)

Language unification for `math.fields.*` (17 nodes). Field math inherits frozen Scalar Math and Random
semantics; sampling nodes enforce a finite **Valid** boundary.
Shared implementation: `FieldMath`, `FieldSampleUtils`, `RandomOps`, `ScalarMathOps`.
Graph schema: **V30** retargets `scalar_sample_points#output_values` to `DOUBLE_LIST` and drops
incompatible downstream wires.

Related: [`node-language-v1-scalar-math.md`](./node-language-v1-scalar-math.md),
[`node-language-v1-random.md`](./node-language-v1-random.md),
[`node-language-v1-point-vector.md`](./node-language-v1-point-vector.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Scalar field math = ScalarMathOps over space** — per-point combine via `FieldMath.combineScalars`.
2. **Noise field = RandomOps.valueNoise3 over space** — same kernel as Random Noise; strict Integer seed.
3. **Sampling is the Valid boundary** — internal fields may be NaN; sample nodes expose finite outputs only.
4. **Batch fail-closed** — one non-finite sample fails the whole batch (`Values=[]`, `Count=0`, `Valid=false`).
5. **Constants reject non-finite** — non-finite scalar or vector components → `output_field=null`.
6. **No hidden clamps** — Noise Scale and SDF Gradient Step use finite/positive resolvers only (no `1e-9` / `1e-4` floors).
7. **Blend weights** — exact `weight != 0.0` (not epsilon-filtered).

## Field types (unchanged)

| Type | Signature | Notes |
|------|-----------|-------|
| `SCALAR_FIELD` | `f(Point) → double` | May be NaN at invalid points internally |
| `VECTOR_FIELD` | `F(Point, dest) → void` | Writes into caller-provided JOML `Vector3d` |

## Sampling contract

| Node | Valid=true | Valid=false |
|------|------------|-------------|
| Scalar Sample Point | finite `Value` | `Value=NaN` |
| Vector Sample Point | all components finite `Vector` | `Vector=null` |
| Scalar Sample Points | all samples finite | `Values=[]`, `Count=0` |
| Vector Sample Points | all samples finite | `Vectors=[]`, `Count=0` |

Invariant: **`Valid=true` ⇒ every numeric output is finite.**

## Inventory (17)

| Node | Type id |
|------|---------|
| Scalar Field Constant | `math.fields.scalar_constant` |
| Scalar Field From SDF | `math.fields.scalar_from_sdf` |
| Scalar Field Noise | `math.fields.scalar_noise` |
| Combine Scalar Fields | `math.fields.scalar_binary_op` |
| Vector Field Constant | `math.fields.vector_constant` |
| Vector Field From SDF Gradient | `math.fields.vector_from_sdf_gradient` |
| Combine Vector Fields | `math.fields.vector_binary_op` |
| Point Attractor Field | `math.fields.point_attractor_field` |
| Path Attractor Field | `math.fields.curve_attractor_field` |
| Volume Attractor Field | `math.fields.volume_attractor_field` |
| Vortex Field | `math.fields.vortex_field` |
| Repulsor Field | `math.fields.repulsor_field` |
| Blend Vector Fields | `math.fields.attractor_blend` |
| Scalar Field Sample Point | `math.fields.scalar_sample_point` |
| Scalar Field Sample Points | `math.fields.scalar_sample_points` |
| Vector Field Sample Point | `math.fields.vector_sample_point` |
| Vector Field Sample Points | `math.fields.vector_sample_points` |

## Scalar Field Noise

Deterministic coherent value noise lifted over world space:

- Seed: `RandomOps.resolveSeed` — `Integer` only; missing ≡ `0`; non-integer ignored.
- Scale: any **finite** double (including `0`, negative).
- Amplitude: finite multiplier.
- Kernel: `RandomOps.valueNoise3((point + offset) * scale, seed) * amplitude`.

Must match Random **Noise** at the same transformed coordinates and seed.

## Combine Scalar Fields

Per-point operations delegate to `ScalarMathOps` (`ADD`, `SUB`, `MUL`, `DIV`, `MIN`, `MAX`, `POW`).

Division by zero → `NaN` at that point (not `Infinity`), consistent with Scalar Math v1.

## Constants

| Input | Rule |
|-------|------|
| Scalar value | finite → constant field; else `null` |
| Vector x/y/z | all finite → constant field; else `null` |

## Generators (parameters)

Port overrides use `FieldMath` resolvers with property/default fallback when invalid:

- **Attractors / Vortex:** Strength finite; Radius/Exponent positive finite.
- **Repulsor:** Strength finite positive.
- **Blend Vector Fields:** `weight != 0.0` exact; weights via `resolveFinite`.

Internal EPS in `AttractorFieldUtils` is for normalize / zero-length vector only — not user weight filtering.

## Graph migration (V29→V30)

For `math.fields.scalar_sample_points`:

- Port `output_values` is now **`DOUBLE_LIST`** (was untyped `LIST`).
- Drop wires **from** `output_values` when target port is not connectable from `DOUBLE_LIST`
  (e.g. → `STRING_LIST` via Sort Text).
- Keep wires to generic `LIST` consumers (e.g. Create List).

No node deletion. No type-id changes.

## Contracts

- `FieldsFamilyContractTest` — spatial roles, typed ports, no `ANY`.
- `FieldLanguageContractTest` — v1 semantics, noise kernel, Valid/finite sampling, V30 migration,
  SDF Step / Attractor parameter resolver fallbacks (invalid port → property default; tiny Step honored).
