# Node Language v2 — Reference Points

**Status: PASSED / FROZEN** (Graph **V87**; V49 remains historical v1)

Language modernization for the nineteen canonical `reference.points.*` nodes:
Valid+Error on all fallible nodes, finite-result fences on point arithmetic,
bounded POINT_LIST inputs, BoxFaceValidator at topology boundaries, query
Valid+Found+Error semantics, StrictDoubleUtils for required DOUBLE inputs, and
OptionalPortDrive on Block Position Input.

Related: [`node-language-v1-reference-points.md`](./node-language-v1-reference-points.md),
[`node-language-v2-reference-planes.md`](./node-language-v2-reference-planes.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## POINT vs BLOCK_POS

Unchanged from v1:

```text
BLOCK_POS = integer block grid cell (Construct Block Position, Block Position Input)
POINT     = continuous geometric position (Construct Point, Block To Point explicit conversion)
```

Continuous math nodes emit `POINT` only. No implicit Point→BlockPos conversion.

## Finite-result rule

**Finite input does not imply finite output.** Every add, subtract, average,
distance, length, and bounds-size operation passes a finite-result fence before
publish. Overflow or non-finite results → `Valid=false` + non-blank `Error`
(and NaN on numeric outputs where v1 used NaN sentinels).

Shared helpers: `PointUtils.safeDistance`, `safeMidpoint`, `safeDisplacement`,
`safeListCenter` (weighted incremental mean: `mean * (n-1)/n + point/n` with
weights precomputed to avoid scaling finite values to Infinity),
`requireFinitePoint`; `StrictDoubleUtils.requireExactFiniteDouble`.

## Inventory (orders 0–18)

| Order | Display name | Type id | Valid+Error | Notes |
|------:|--------------|---------|:-----------:|-------|
| 0 | Block Position Input | `reference.points.block_position` | yes | OptionalPortDrive on X/Y/Z |
| 1 | Construct Block Position | `reference.points.construct_coordinate` | yes | exact INTEGER |
| 2 | Deconstruct Block Position | `reference.points.deconstruct_block_position` | yes | 0 sentinel on invalid |
| 3 | Block To Point | `reference.points.point_from_block` | yes | explicit conversion |
| 4 | Construct Point | `reference.points.construct_point` | yes | strict DOUBLE |
| 5 | Deconstruct Point | `reference.points.deconstruct_point` | yes | NaN components on invalid |
| 6 | Translate Point | `reference.points.translate_point` | yes | finite add fence |
| 7 | Move Along Direction | `reference.points.point_along_vector` | yes | strict DOUBLE distance |
| 8 | Mid Point | `reference.points.mid_point` | yes | safeMidpoint |
| 9 | Distance Between Points | `reference.points.distance_between_points` | yes | safeDistance |
| 10 | Vector Between Points | `reference.points.vector_between_points` | yes | safeDisplacement |
| 11 | Closest Point | `reference.points.closest_point` | yes | bounded list, safeDistance compare |
| 12 | Point List Center | `reference.points.point_list_center` | yes | bounded list, safeListCenter |
| 13 | Point List Bounds | `reference.points.point_list_bounds` | yes | bounded list, finite size |
| 14 | Get Box Corner | `reference.points.get_box_corner` | yes | Valid+Found+Error |
| 15 | Get Box Face | `reference.points.get_box_face` | yes | Valid+Found+Error, BoxFaceValidator output |
| 16 | Get Face Edge | `reference.points.get_face_edge` | yes | Valid+Found+Error, BoxFaceValidator |
| 17 | Deconstruct Box Face | `reference.points.deconstruct_face` | yes | BoxFaceValidator preflight |
| 18 | Deconstruct Face Edge | `reference.points.deconstruct_edge` | yes | safe length/midpoint/vector |

## Query semantics (Valid + Found + Error)

| State | Valid | Found | Error |
|-------|:-----:|:-----:|-------|
| Bad input type / topology | false | false | non-blank |
| Legal query, no match | true | false | empty |
| Success | true | true | empty |

Examples:
- Unknown face name `"banana"` → Valid=true, Found=false
- Connected null face name → Valid=false
- Out-of-range corner index → Valid=true, Found=false
- Non-BOX_GEOMETRY input → Valid=false

## Bounded POINT_LIST

Closest Point, Point List Center, and Point List Bounds use
`PointUtils.resolveStrictPointListBounded(GenerationLimits.MAX_LIST_ELEMENTS)`.

## Migration (V86 → V87)

Format bump only. Error ports additive; query nodes gain `output_valid`; node IDs
and port IDs unchanged; no wire remap. Stricter runtime semantics for overflow,
strict DOUBLE, and query Valid/Found split.
