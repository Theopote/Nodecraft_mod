# Node Language v2 — Random

**Status: PASSED / FROZEN** (`GraphFormatVersion.CURRENT` is stamp-only; V123 remains historical residue)

Strict domain & transactional sampling for `math.random.*`:
`output_valid` / `output_error` on all six nodes, connection-aware Seed/Count/Domain,
canonical `VectorData` on VECTOR ports, and whole-vector / whole-list failure semantics.

Related: [`node-language-v1-random.md`](./node-language-v1-random.md),
[`node-language-v2-scalar-math.md`](./node-language-v2-scalar-math.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Valid / Error contract

All six random nodes expose:

| Port | Type | Meaning |
|------|------|---------|
| Primary output | varies | Sample result when valid |
| `output_valid` | BOOLEAN | Whether sampling succeeded |
| `output_error` | STRING | Error message when invalid |

| State | Valid | Primary output |
|-------|-------|----------------|
| Success | `true` | finite sample / opaque payload |
| Failure | `false` | `NaN`, `null`, or `[]` as documented per node |

Invariant: **`Valid=false` ⇒ failure sentinel output** (never a silent default sample).

PURE nodes. Missing Seed ≡ `0` (deterministic, not a fresh seed each evaluate).

## Connection-aware Seed / Count / Domain

Graph ports use `RandomInputResolver`, **not** `RandomOps.resolveSeed` / `resolveCount` (legacy helpers for other families).

| Port state | Seed | Count |
|------------|------|-------|
| undriven | `0` (valid) | property default (valid, **clamped** to `MAX_LIST_ELEMENTS`) |
| driven + exact `Integer` in range | value | value (`<=0` → empty success) |
| driven + exact `Integer` `> MAX_LIST_ELEMENTS` | — | **invalid** (no silent truncate) |
| driven + null / wrong type | **invalid** | **invalid** |

Domain (Number / Numbers):

- undriven → property defaults (valid)
- driven + finite-endpoint `NUMERIC_RANGE` → keep span (including overflow-directed)
- driven + invalid → `Valid=false`

Sampling uses overflow-safe FMA lerp when `hi - lo` overflows. Finite endpoints remain sampleable.

## VECTOR contract

Internal sampling uses JOML `Vector3d`. Graph values are canonical [`VectorData`](../src/main/java/com/nodecraft/nodesystem/datatypes/VectorData.java):

- ingest: `VectorUtils.toVector` (`VectorData` / `Vector3d` / legacy `Vec3d`), finite required
- emit: `VectorUtils.toVectorPort` / `toVectorPortList`

| Port state | Min / Max Corner |
|------------|------------------|
| undriven | default unit-box corner |
| driven + valid VECTOR | use value |
| driven + invalid / null | `Valid=false` (never `(0,0,0)` / `(1,1,1)`) |

## Node failure outputs

| Node | Failure |
|------|---------|
| Random Number | `Random=NaN` |
| Random Numbers | `Values=[]` (not short list) |
| Random List Item | `Item=null`, `Items=[]` |
| Random Vector | `Vector=null` |
| Random Vectors | `Vectors=[]` (transactional) |
| Noise | `Noise=NaN` |

## Vector transactional sampling

`RandomOps.sampleVectorValidated` preflights all three axes. Non-finite axis or non-finite **result** component → entire vector fails. Overflow span alone is not a reject if the FMA sample is finite.

`Random Vectors` is transactional: one invalid sample → entire list fails (`[]`).

## Random List Item

- Same list content, same order, same seed → same picks
- **Allow Duplicates=false** samples **without replacement by index** (partial Fisher–Yates of K), not value-unique (`[A,A,B]` can return two `A`s)
- Count is capped to source size when sampling without replacement
- Allow Duplicates=true uses `nextInt` with replacement

## Noise (algorithm unchanged)

Coherent 3D value noise via `RandomOps.valueNoise3`. Non-finite coordinates or unsafe lattice cells → invalid.

## Preserved from v1

- Determinism: same inputs + seed ⇒ same outputs
- Missing Seed ≡ `0` when **undriven**
- Stable port types (no Count-driven output switching)
- No Number/String coercion on Seed/Count

## Breaking changes from v1

| Scenario | V29 | Current |
|----------|-----|---------|
| Invalid Domain | silent `0` or short `[]` | `Valid=false` |
| Connected Count=`1.9` | default 10 | `Valid=false` |
| Connected Count over list cap | silent clamp | `Valid=false` |
| Connected Seed=`"1"` | treated as `0` | `Valid=false` |
| VECTOR output | raw `Vector3d` | `VectorData` |
| Partial NaN vector | emitted | `Valid=false` |

## Migration

No `GraphFormatVersion` bump. `CURRENT` is stamp-only.
