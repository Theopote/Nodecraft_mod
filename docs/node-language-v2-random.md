# Node Language v2 — Random

**Status: PASSED / FROZEN** (Graph **V123**; V29 remains historical v1)

Strict domain & transactional sampling remediation for `math.random.*`:
`output_valid` / `output_error` on all six nodes, connection-aware Seed/Count/Domain,
and whole-vector / whole-list failure semantics.

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

## Connection-aware Seed / Count / Domain

| Port state | Seed | Count |
|------------|------|-------|
| undriven | `0` (valid) | property default (valid, clamped) |
| connected + exact type | value (valid) | clamped value (valid) |
| connected + null / wrong type | **invalid** | **invalid** |

Domain (Number / Numbers):

- undriven → property defaults (valid)
- driven + valid `NUMERIC_RANGE` → canonical domain
- driven + invalid → `Valid=false`

Vector corners (Vector / Vectors):

- undriven → default unit box corners
- driven + `VECTOR` / legacy `Vec3d` → use copy
- driven + invalid → `Valid=false`

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

`RandomOps.sampleVectorValidated` preflights all three axes before sampling.
If any axis span is non-finite or overflows, the entire vector fails — no partial `(x,y,NaN)` vectors.

`Random Vectors` uses batch transactional semantics: one invalid sample → entire list fails (`[]`).

## Random List Item semantics (unchanged algorithm)

- Same list content, same order, same seed → same picks
- Reordering the input list changes results (index-based sampling)
- **Allow Duplicates=false** means sample **without replacement by index**, not value-unique
  (e.g. `[A,A,B]` can still return two `A` items from different indices)

## Noise (algorithm unchanged)

Coherent 3D value noise via `RandomOps.valueNoise3`. Non-finite coordinates or unsafe lattice cells → invalid.

## Preserved from v1

- Determinism: same inputs + seed ⇒ same outputs
- Missing Seed ≡ `0` when **undriven**
- Stable port types (no Count-driven output switching)
- No Number/String coercion on Seed/Count
- Random List Item shuffle / `nextInt` sampling unchanged

## Breaking changes from v1

| Scenario | V29 | V123 |
|----------|-----|------|
| Invalid Domain | silent `0` or short `[]` | `Valid=false` |
| Connected Count=`1.9` | default 10 | `Valid=false` |
| Connected Seed=`"1"` | treated as `0` | `Valid=false` |
| Partial NaN vector | emitted | `Valid=false` |
| Random Vectors bad domain | partial list possible | entire list fails |

## Graph migration (V122→V123)

Identity migration — no wire or node remaps. New output ports are additive.

## P2 deferred

Separate vector-list memory budget (`MAX_LAYOUT_INSTANCES` vs `MAX_LIST_ELEMENTS`) — evaluation deferred.
