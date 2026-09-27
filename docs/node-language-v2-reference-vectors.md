# Node Language v2 — Reference Vectors

**Status: PASSED / FROZEN** (Graph **V88**; V50/V51 remain historical v1)

Language modernization for the seventeen canonical `reference.vectors.*` nodes:
Valid+Error on all nodes, finite-result fences on vector arithmetic,
`safeLength` / `safeNormalize` / `safeLerp`, strict exact-Double inputs,
unit-axis Project formulation, and preserved zero-VECTOR semantics.

Related: [`node-language-v1-reference-vectors.md`](./node-language-v1-reference-vectors.md),
[`node-language-v2-reference-points.md`](./node-language-v2-reference-points.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## VECTOR invariant

Unchanged from v1:

```text
VECTOR = finite 3D direction / displacement
ZERO VECTOR = valid VECTOR
```

Direction-requiring operations reject zero (`|V| > EPS` via `safeLength`):
Normalize, Angle Between, Slerp, Reflect normal, Project axis B.

## Finite-result rule

**Finite input does not imply finite output.** Every length, dot, cross,
add/sub/mul/div, lerp/slerp, reflect, and project operation passes a
finite-result fence before publish.

Shared helpers: `VectorUtils.safeLength`, `safeNormalize`, `safeAdd`,
`safeSubtract`, `safeScale`, `safeDot`, `safeCross`, `safeLerp`, `safeScalarLerp`
(FMA formulation: `Math.fma(t, b, Math.fma(-t, a, a))` for extrapolation stability);
`StrictDoubleUtils.requireExactFiniteDouble`; `OptionalPortDrive.resolveOptionalStrictDouble`.

## Inventory (orders 0–16)

| Order | Display name | Type id | Valid+Error |
|------:|--------------|---------|:-----------:|
| 0 | Vector Input | `reference.vectors.vector` | yes |
| 1 | 2D Vector Input | `reference.vectors.vector2_input` | yes |
| 2 | Construct Vector | `reference.vectors.construct_vector` | yes |
| 3 | Deconstruct Vector | `reference.vectors.deconstruct_vector` | yes |
| 4 | Vector Length | `reference.vectors.vector_length` | yes |
| 5 | Normalize Vector | `reference.vectors.normalize_vector` | yes |
| 6 | Vector Addition (+) | `reference.vectors.vector_addition` | yes |
| 7 | Vector Subtraction (-) | `reference.vectors.vector_subtraction` | yes |
| 8 | Vector Scalar Multiply | `reference.vectors.vector_scalar_multiply` | yes |
| 9 | Vector Scalar Divide | `reference.vectors.vector_scalar_divide` | yes |
| 10 | Dot Product | `reference.vectors.dot_product` | yes |
| 11 | Cross Product | `reference.vectors.cross_product` | yes |
| 12 | Angle Between Vectors | `reference.vectors.angle_between` | yes |
| 13 | Lerp Vectors | `reference.vectors.lerp_vectors` | yes |
| 14 | Slerp Vectors | `reference.vectors.slerp` | yes |
| 15 | Reflect Vector | `reference.vectors.reflect` | yes |
| 16 | Project Vector onto Vector | `reference.vectors.project` | yes |

Component Min/Max remains in `math.vector.component_minmax`.

## Preserved V51 semantics

- Zero VECTOR valid for displacement ops (Add, Dot, Cross, Length, Reflect incoming)
- Lerp/Slerp T **not clamped** (T=2 extrapolation allowed)
- Degrees-only Angle output
- Antiparallel Slerp deterministic semicircle (no shortest-path negate)
- Divide near-zero EPS threshold unchanged
- Vector3d/Vec3d input compatibility retained (P2)

## Invalid output policy

```text
invalid VECTOR → null
invalid DOUBLE → NaN
Valid=false, Error non-blank on failure
```

Never use `(0,0,0)` as failure sentinel — zero is valid data.

## Migration (V87 → V88)

Format bump only. Error ports additive; node IDs and port IDs unchanged;
no wire remap. Stricter runtime semantics for overflow and strict DOUBLE.
