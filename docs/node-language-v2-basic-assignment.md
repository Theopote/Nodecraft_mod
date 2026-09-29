# Node Language v2 — Basic Assignment

**Status: PASSED / FROZEN** (Graph **V114**; V40 remains historical v1)

Strict source & weight remediation for `material.basic_assignment.*`: finite total
weight, exact `DOUBLE_LIST`, fail-closed placement/tree parsing, and connection-aware
source precedence so driven-invalid inputs never fall through to the next source.

Shared helper: `MaterialSourceResolver` (+ `BasicAssignmentUtils`). Soft
`MaterialMappingSupport.extractPlacements` for other material families is unchanged.

Related: [`node-language-v1-basic-assignment.md`](./node-language-v1-basic-assignment.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Finite total weight

```text
each weight: finite && >= 0
after each sum += weight: isFinite(sum)
final: sum > 0
```

`Valid=true` never emits `output_total_weight` of `Infinity` / `NaN`.
`Double.MAX_VALUE + Double.MAX_VALUE` → `Valid=false`.

## Exact DOUBLE_LIST

Weights accept only exact `Double` entries (same spirit as strict `STRING_LIST`).
`Integer` / `Long` / `Float` lists → `Valid=false`. No runtime Number coercion.

## Source precedence

| Order | Weighted / Block Palette | Assign |
|-------|--------------------------|--------|
| 1 | Placements Tree | Blocks Tree |
| 2 | Blocks Tree | Placements |
| 3 | Placements | Coordinates |
| 4 | Coordinates | Geometry |
| 5 | Geometry | — |

Geometry subtype order: GEOMETRY → BOX → CYLINDER → SPHERE → TORUS (first driven wins).

## Driven port rule

| State | Behavior |
|-------|----------|
| Not driven | skip to next precedence |
| Driven + valid | use; stop |
| Driven + invalid | `Valid=false`; **no** fallback |

Driven = wire connected **or** non-null injected input value (unit-test `setInput`).

## Fail-closed parsers

| Input | Rule |
|-------|------|
| `BLOCK_PLACEMENT_LIST` | every entry `BlockPlacementData` with non-null `pos` |
| `DATA_TREE` | every item `BlockPos` or `BlockPlacementData` with non-null `pos` |
| Coordinates | `BlockPosList`; no null positions |
| Geometry | voxelize; empty result → fail |

Empty connected placements / empty tree = valid empty source (does not fall through).

## Empty palette semantics (unchanged)

| Source | Empty palette |
|--------|---------------|
| Placements / Placements Tree | preserve `source.blockId` |
| Blocks Tree / Coordinates / Geometry | `Valid=false` |

## Migration

Graph **V113→V114** is a no-op (Valid semantics; no wire remaps).

## Contract

- `BasicAssignmentLanguageV2ContractTest` — V114 fence, finite sum, exact Double,
  mixed placements/tree fail-closed, no connected-invalid fallback, weight size mismatch.
- `BasicAssignmentLanguageContractTest` — V40 inventory / RandomOps retained.
