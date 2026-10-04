# Node Language v2 — Vector

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only)

Graph `VECTOR` / `VECTOR_LIST` are `VectorData` only. JOML `Vector3d` remains internal scratch math.

Related: [`node-language-v2-reference-vectors.md`](./node-language-v2-reference-vectors.md),
[`node-language-v2-scalar-math.md`](./node-language-v2-scalar-math.md).

## Contract

```text
ingest:  VectorData → JOML Vector3d
math:    JOML (finite-result fences as documented per node)
emit:    VectorUtils.toVectorPort / toVectorPortList → VectorData
```

- Compact `VectorData` constructor requires finite components (`IllegalArgumentException` otherwise).
- Zero vector is valid data. Failure sentinels are `null` (VECTOR) / empty list or `null` (VECTOR_LIST), never `(0,0,0)`.
- `VectorData.canonical(Vector3d)` returns `null` for non-finite (fail-closed emit, no throw).
- Graph ingest: `VectorUtils.toStrictVectorPortValue` / `resolveStrictVectorList`. Raw `Vector3d` / `Vec3d` fail closed.
- `VectorUtils.toVectorLegacy` is world/internal only. `toVector` is deprecated.

## Inventory

| Order | Display name | Type id | Valid+Error |
|------:|--------------|---------|:-----------:|
| 0 | Component Min/Max | `math.vector.component_minmax` | yes |

`reference.vectors.*` remains the arithmetic family. Random Vector(s) and Field vector sample already emit `VectorData`.

## Result + Valid

| State | Valid | VECTOR outputs |
|-------|-------|----------------|
| Success | `true` | finite `VectorData` |
| Failure | `false` | `null` |

## Out of scope (this freeze)

Material families, rewriting Component Min/Max math, deleting `RandomOps.resolveVector`.
