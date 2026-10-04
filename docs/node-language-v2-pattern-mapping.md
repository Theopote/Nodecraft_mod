# Node Language v2 — Pattern Mapping

**Status: PASSED / FROZEN** (current graph format; V38 remains historical v1)

Strict coordinates & source remediation for `material.pattern_mapping.*`: long
`Relative` coords, long-safe Brick Auto/stagger indexing, shared
`MaterialSourceResolver`, and connection-aware Pattern Origin.

Related: [`node-language-v1-pattern-mapping.md`](./node-language-v1-pattern-mapping.md),
[`node-language-v2-gradient-mapping.md`](./node-language-v2-gradient-mapping.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Source precedence

Flat ports only (no tree ids):

```text
Placements → Coordinates → Geometry (GEOMETRY → BOX → CYLINDER → SPHERE → TORUS)
```

| State | Behavior |
|-------|----------|
| Not driven | skip to next precedence |
| Driven + valid | use; stop |
| Driven + invalid | `Valid=false`; **no** geometry/coords fallthrough |
| Driven + empty list | valid empty (no fallthrough) |

Shared with Basic Assignment / Directional / Gradient:
`com.nodecraft.nodesystem.util.MaterialSourceResolver`.

## Relative coordinates

`PatternMaterialUtils.Relative` uses `long dx/dy/dz`:

```text
dx = (long) pos.x - origin.x
```

| Node | Arithmetic |
|------|------------|
| Checker | LSB XOR parity: `(dx ^ dy ^ dz) & 1` |
| Stripe | `floorDiv(d{axis}, stripeWidth)` on long |
| Grid | `floorMod(dx/dz, gridSize)` on long |
| Brick | long `brickIndex` / Auto spans |

## Pattern Origin

| Port state | Behavior |
|------------|----------|
| Undriven | `(0,0,0)` |
| Driven + `BlockPos` | use it |
| Driven + null / wrong type | `Valid=false` |

## Brick Auto / stagger

Auto axis compares long X/Z spans (no int overflow; no packing relative longs into
temporary `BlockPos`). Stagger add and `floorDiv` run in `long`.

## Migration

Graph format is stamp-only (`CURRENT = 1`); there is no V117→V118 wire remap.

## Contract

- `PatternMappingLanguageV2ContractTest` — current-format fence, mixed placements, no
  connected-invalid fallback, extreme relative coords, Brick long-safe, driven
  Origin fail, undriven Origin smoke.
- `PatternMappingLanguageContractTest` — V38 inventory retained.
- `BrickPatternMappingTest` — long spans / stagger near int max.
