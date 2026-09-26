# Node Language v1 — Reference Vectors

**Status: PASSED / FROZEN** (Graph **V51**)

Language unification for the seventeen canonical `reference.vectors.*` nodes: finite VECTOR
semantics (zero vector valid), connected-vs-unconnected optional inputs, null invalid outputs,
Slerp geodesic (including antiparallel semicircle), shared `VectorUtils`, typed `VectorData`
payloads, and unique node ordering 0–16.

Related: [`node-language-v1-reference-points.md`](./node-language-v1-reference-points.md),
[`node-language-v1-reference-planes.md`](./node-language-v1-reference-planes.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## VECTOR invariant

```text
VECTOR = finite 3D direction / displacement
zero VECTOR is valid
runtime payload = VectorData (legacy Vector3d / Vec3d still accepted on inputs)
```

Zero displacement is valid data (`A + (-A) = 0`, `0 · B = 0`, `|0| = 0`).

Operations requiring a **direction** additionally require `|V| > EPS`
(implemented as `lengthSquared() > EPS_SQ`):

- Normalize Vector
- Angle Between Vectors
- Slerp Vectors
- Reflect Vector (normal)
- Project Vector onto Vector (axis B)
- Move Point Along Direction (points family)

## Invalid output policy

```text
invalid VECTOR → null
invalid DOUBLE → NaN
Valid          → false
```

Do not use `(0,0,0)` or `(0,1,0)` as failure sentinels.

## Connected vs unconnected optional inputs

Optional override ports use **port connection state**, not `inputValues != null`:

```text
unconnected              → property fallback
connected + valid input  → input override
connected + null/invalid → fail closed (no property fallback)
```

Applies to Vector Input X/Y/Z and Angle Between Vectors Reference.

## Shared VectorUtils / SpatialTolerance

Single helper class: `com.nodecraft.nodesystem.util.VectorUtils`

Public API:
- `EPS`, `EPS_SQ` (aliases of `SpatialTolerance`)
- `isFinite`, `isNonZero` (`lengthSquared() > EPS_SQ`)
- `toVector(Object)` — strict VECTOR port (`VectorData`, `Vector3d`, legacy `Vec3d`)
- `toVectorPort(Vector3d)` — canonical `VectorData` for VECTOR outputs

Finiteness delegates to `FrameUtils.isFinite`.

`PointUtils` / `PlaneUtils` / `FrameUtils` / `VectorUtils` share `SpatialTolerance.EPS` and
`EPS_SQ`. Squared-length comparisons use `EPS_SQ`; linear scalar comparisons use `EPS`.

## Inventory (17)

| order | Display name | Type id | Role |
|------:|--------------|---------|------|
| 0 | Vector Input | `reference.vectors.vector` | Property + optional X/Y/Z drive |
| 1 | 2D Vector Input | `reference.vectors.vector2_input` | 2D property → VECTOR(x,y,0) |
| 2 | Construct Vector | `reference.vectors.construct_vector` | X/Y/Z doubles → VECTOR |
| 3 | Deconstruct Vector | `reference.vectors.deconstruct_vector` | VECTOR → X/Y/Z |
| 4 | Vector Length | `reference.vectors.vector_length` | Magnitude |
| 5 | Normalize Vector | `reference.vectors.normalize_vector` | Unit direction |
| 6 | Vector Addition | `reference.vectors.vector_addition` | A + B |
| 7 | Vector Subtraction | `reference.vectors.vector_subtraction` | A - B |
| 8 | Vector Scalar Multiply | `reference.vectors.vector_scalar_multiply` | V * s |
| 9 | Vector Scalar Divide | `reference.vectors.vector_scalar_divide` | V / s |
| 10 | Dot Product | `reference.vectors.dot_product` | A · B |
| 11 | Cross Product | `reference.vectors.cross_product` | A × B |
| 12 | Angle Between Vectors | `reference.vectors.angle_between` | Degrees; optional signed Reference |
| 13 | Lerp Vectors | `reference.vectors.lerp_vectors` | Linear interpolation (T not clamped) |
| 14 | Slerp Vectors | `reference.vectors.slerp` | Spherical interpolation |
| 15 | Reflect Vector | `reference.vectors.reflect` | Reflection across normal |
| 16 | Project Vector onto Vector | `reference.vectors.project` | Projection / rejection |

Moved at V51: Vector Component Min/Max → `math.vector.component_minmax`.

## Cross Product

Finite inputs always yield `Valid=true`. Parallel or zero inputs produce zero cross product
(`(1,0,0) × (2,0,0) = 0`) — this is valid, not an error.

## Slerp geodesic semantics

Standard vector slerp on normalized directions:

```text
θ = acos(clamp(A·B, -1, 1))
```

No quaternion-style `B.negate()` when `A·B < 0`. At `T=0` result direction matches A; at `T=1`
matches B (including when dot is negative).

Antiparallel vectors (`A·B ≈ -1`) use a deterministic orthogonal plane and a continuous
semicircle:

```text
result = A * cos(πT) + perp * sin(πT)
```

Endpoints still match A at T=0 and B (= -A) at T=1; T=0.5 is perpendicular to A.

Removed at V50: `shortestPath` property (was incorrect for ordinary vectors).
Fixed at V51: antiparallel path no longer uses linear-lerp normalize (which collapsed to A/B).

## Angle Between Vectors

- Unsigned and signed outputs are **degrees**.
- Reference unconnected: unsigned valid, signed = NaN, Valid = true (when A,B non-zero).
- Reference connected but invalid/null/zero: fail closed (Valid = false).
- Reference is typically the plane normal of the plane containing A and B; signed angle uses
  `atan2(ref · (A×B), A·B)`.

## Lerp extrapolation

`T` is **not** clamped to [0,1]. `T=2` extrapolates beyond B.

## Producer compactness (V50)

- **2D Vector Input:** VECTOR output only (removed X/Y/UV echo ports).
- **Reflect Vector:** Reflected + Valid only (removed Normalized Normal echo).

Use Deconstruct Vector / Normalize Vector downstream when components or unit normal are needed.

## Deferred (non-blocking)

- 2D Vector Input: reject non-finite property values at setter / state load (keep Vector-only)
- `VectorData` constructor finite invariant at datatype layer (producers already use `canonical()`)
