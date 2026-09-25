# Node Language v1 — Random

Freeze for `math.random.*` deterministic seeded procedural variation.
Shared implementation: `com.nodecraft.nodesystem.math.RandomOps`.
Graph schema: **V29** drops Random Vector `input_count` and `output_random_vector` wires.

Related: [`node-language-v1-sequence.md`](./node-language-v1-sequence.md) (INTEGER Count),
[`node-language-v1-logic.md`](./node-language-v1-logic.md) (no truthy coercion),
[`node-language-v1-point-vector.md`](./node-language-v1-point-vector.md) (VECTOR = JOML `Vector3d`),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Core rules

1. **Determinism** — same inputs + same seed ⇒ same outputs. Missing Seed ≡ `0`. Seed `0` is valid.
2. **Stable port types** — no Count-dependent output type switching.
3. **Strict INTEGER** — Seed and Count accept `Integer` only (no `Number`/`String` coercion).
4. **Strict BOOLEAN** — Allow Duplicates is `instanceof Boolean b && b` only.
5. **Finite samples** — valid finite domains produce finite numeric outputs; overflowed span → NaN.
6. **Hard cap** — list counts use `GenerationLimits.clampNonNegativeCount`.

## Inventory (6)

| Node | Type id | Output |
|------|---------|--------|
| Random Number | `math.random.random_number` | `DOUBLE` |
| Random Numbers | `math.random.random_numbers` | `DOUBLE_LIST` |
| Random List Item | `math.random.random_list_item` | Item `T` + Items `List<T>` |
| Random Vector | `math.random.random_vector` | `VECTOR` (JOML) |
| Random Vectors | `math.random.random_vectors` | `VECTOR_LIST` |
| Noise | `math.random.noise` | `DOUBLE` coherent value noise |

## Random Number / Random Numbers

Domain + Seed → single or list. Sample via `RandomOps.sampleDouble`.
Non-Integer Count on Numbers → property default, then clamp.

## Random Vector / Random Vectors

Symmetric to Number / Numbers:

- **Random Vector** — Min + Max + Seed → one `VECTOR` (no Count)
- **Random Vectors** — Min + Max + Count + Seed → `VECTOR_LIST`

Internal type is always `org.joml.Vector3d`. Legacy Minecraft `Vec3d` may be accepted as input for robustness; outputs are JOML only.

## Random List Item

| Port | Type |
|------|------|
| List | `LIST` + `bindListType("T")` |
| Count | `INTEGER` |
| Allow Duplicates | `BOOLEAN` |
| Seed | `INTEGER` |
| Item | `ANY` + `bindListElementType("T")` |
| Items | `LIST` + `bindListType("T")` |

No scalar auto-wrap. Empty / Count 0 → Item=`null`, Items=`[]`.
Item = first selected; Items = full selection (kept even when Count=1).

## Noise

Deterministic 3D **value noise** (lattice hash + smoothstep + trilinear), roughly `[-1, 1]`.
Non-finite X/Y/Z → `NaN`. Nearby coordinates produce smoothly related values (not hash jumps).

## Graph migration (V28→V29)

For `math.random.random_vector`:

- Drop wires **to** `input_count`
- Drop wires **from** `output_random_vector`

Do **not** remap old multi-vector graphs to Random Vectors (pre-release: cannot know runtime type).
New single-vector output port id: `output_vector`.

For type tightening (declared-type compatibility via `NodeDataType.isConnectableTo`):

- `math.random.random_list_item`
  - Drop wires **to** `input_list` whose source is not list-connectable (e.g. STRING→LIST)
  - Drop wires **from** `output_items` / `output_item` whose target is not connectable
  - Valid `LIST`→`input_list` and `output_item`→`ANY` consumers are kept
- `math.random.random_numbers`
  - Drop wires **from** `output_values` incompatible with `DOUBLE_LIST` (e.g. → `STRING_LIST`)

## Noise lattice safety

`valueNoise3` uses `long` lattice coordinates. If the fractional cell is not in `[0,1)`
(e.g. coordinates beyond safe long range such as `1e20`), the result is `NaN` —
never an unbounded fade overflow.
