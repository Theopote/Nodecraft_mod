# Node Language v2 — Gradient Mapping

**Status: PASSED / FROZEN** (Graph **V117**; V37 remains historical v1)

Strict source & finite-domain remediation for `material.gradient_mapping.*`: shared
`MaterialSourceResolver`, cast-before-subtract height Y-span, finite positive
distance domain width, and driven-aware exact `Double` optional ports.

Related: [`node-language-v1-gradient-mapping.md`](./node-language-v1-gradient-mapping.md),
[`node-language-v2-directional-mapping.md`](./node-language-v2-directional-mapping.md),
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

Shared with Basic Assignment / Directional Mapping:
`com.nodecraft.nodesystem.util.MaterialSourceResolver`.

## Height Y-span

Height Gradient Map and Height Palette Map normalize with:

```text
span = (double) maxY - (double) minY
t = singleHeight ? 0 : ((double) y - (double) minY) / span
```

Never subtract `maxY - minY` in `int` before casting. Single-height columns still
map to Bottom / first palette bin (`t = 0`).

## Distance domain

`GradientMaterialUtils.requirePositiveWidth(min, max)`:

- endpoints finite
- `span = max - min` finite and `> 0`

`±Double.MAX_VALUE` → `Valid=false` (non-finite span). Aligns with
`NumericRangeData.canonical` span discipline.

## Optional DOUBLE ports

Noise thresholds, Distance min/max, SDF center / half-width:

| Port state | Behavior |
|------------|----------|
| Undriven | property/default fallback |
| Driven | exact finite `Double` or `Valid=false` |

No silent `Number.doubleValue()` coercion. Driven = wire connected **or** non-null
injected input (`MaterialSourceResolver.isDriven`).

## Migration

Graph **V116→V117** is a no-op (Valid semantics; no wire remaps).

## Contract

- `GradientMappingLanguageV2ContractTest` — V117 fence, mixed placements, no
  connected-invalid fallback, extreme Y span, non-finite distance domain, driven
  non-Double fail, single-Y / empty-palette smoke.
- `GradientMappingLanguageContractTest` — V37 inventory retained.
