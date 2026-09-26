# Node Language v1 — Morphology

**Status: PASSED / FROZEN** (Graph **V57**)

Binary morphology on block sets under `utilities.morphology` (1 node). The frozen rule for this
family is: **set semantics, strict BLOCK_LIST payload, GenerationLimits workload cap, exact
INTEGER iterations, transactional fail-closed (no partial morphology).**

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`contracts/preview-side-effects.md`](./contracts/preview-side-effects.md) (`PURE`).

## Product boundary

```text
morphology = Block Morphology (1)
BLOCK_LIST: BlockPosList + List<BlockPos>; strict members; empty valid
set semantics: dedupe input; output order deterministic but non-semantic
exact INTEGER iterations 1..MAX_MORPHOLOGY_ITERATIONS
MAX_MORPHOLOGY_BLOCKS protects input/intermediate/output
dilation early abort; over budget fail closed (no partial)
invalid → Delta=0; Error port
D6 / D26 dilate/erode core unchanged
```

## Inventory (1)

| order | Display name | Type id |
|------:|--------------|---------|
| 0 | Block Morphology | `utilities.morphology.block_list_morphology` |

## BLOCK_LIST input

Resolved via `BlockListUtils.resolveStrictBlockSet()`:

```text
BlockPosList or List<BlockPos>  → accepted, canonicalized to unique set
null / wrong type / null member → invalid (Valid=false)
empty collection              → valid empty output
duplicate positions           → ignored (set semantics)
```

`Input Count` reports **unique** input block count.

## Block Morphology

- Effect: **`PURE`**
- Properties: `Operation` (DILATE / ERODE), `Connectivity` (SIX / TWENTY_SIX), `Iterations` (default 1)
- Optional port: `Iterations` via `OptionalPortDrive.resolveOptionalInteger` — exact `Integer` only, range `1..MAX_MORPHOLOGY_ITERATIONS`; no silent clamp
- Workload cap: `GenerationLimits.MAX_MORPHOLOGY_BLOCKS` (262_144) on input, intermediate, and output sets
- Dilation checks cap **during** neighbor generation (early abort)
- Over budget or invalid → empty `BLOCK_LIST`, `Valid=false`, `Delta Count=0`, `Error` message — **never partial morphology**
- Coordinate offsets use overflow-safe addition; overflow → fail closed

### Structuring elements

```text
SIX:          ±X, ±Y, ±Z (6 neighbors)
TWENTY_SIX:   dx,dy,dz ∈ {-1,0,1} \ {0,0,0}
```

### Outputs

```text
Blocks        BLOCK_LIST   (deterministic order, set semantics)
Input Count   INTEGER      (unique input size)
Output Count  INTEGER
Delta Count   INTEGER      (output - input when Valid; 0 when invalid)
Valid         BOOLEAN
Error         STRING
```

No `Max Output Blocks` port/property/state.

## Migration

`migrateV56ToV57`: strips `maxOutputBlocks` state; drops wires to `input_max_output_blocks`;
remaps `output_stopped_reason` → `output_error`.
