# Node Language v2 — Directional Mapping

**Status: PASSED / FROZEN** (Graph **V116**; V36 remains historical v1)

Strict sources & normals remediation for `material.directional_mapping.*`: shared
`MaterialSourceResolver`, registry-validated mapped `BLOCK_TYPE`, non-zero normals,
angle fail-closed in `processNode`, and classification-count diagnostics.

Related: [`node-language-v1-directional-mapping.md`](./node-language-v1-directional-mapping.md),
[`node-language-v2-basic-assignment.md`](./node-language-v2-basic-assignment.md),
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

Shared with Basic Assignment: `com.nodecraft.nodesystem.util.MaterialSourceResolver`.

## Mapped BLOCK_TYPE

`MaterialMappingSupport.requireKnownBlockType(value, driven)`:

| Port state | Result |
|------------|--------|
| Undriven | absent (`null` id) — preserve source `blockId` |
| Driven blank / non-String | `Valid=false` |
| Driven unknown registry id | `Valid=false` (`Unknown block: …`) |
| Driven known id | use that id |

Coords/geometry voxelization still requires at least one known mapped material
(`firstMappedBlockType` / Default Block). Empty live registry (unit tests) accepts
well-formed identifiers; once `Registries.BLOCK` is populated, membership is required.

## Slab / Stair — normals

Each `VECTOR_LIST` element must resolve to a finite non-zero vector
(`lengthSquared() > 1e-9`). Else `Valid=false`:
`Normals must be finite non-zero vectors`.

Zero normals never reach `chooseMaterial` (no DEFAULT branch for zero).

Count mismatch / missing normals when source non-empty remain fail-closed.

## Angles (processNode)

| Rule | On violation |
|------|--------------|
| `slabAngle` / `stairAngle` finite and in `[0, 90]` | `Valid=false` |
| `stairAngle >= slabAngle` | `Valid=false` |

UI setters may still clamp on interactive edit; `processNode` does **not** repair
corrupt saved state.

## Classification counts

Port ids unchanged: `output_slab_count` / `output_stair_count`.

Display names: **Slab-Classified** / **Stair-Classified** — counts of normal-angle
classification bands, not “blocks remapped to slab/stair ids”. Missing slab/stair
mapped type still increments classification while preserving source `blockId`.

## Migration

Graph **V115→V116** is a no-op (Valid semantics; no wire remaps).

## Contract

- `DirectionalMappingLanguageV2ContractTest` — V116 fence, mixed placements, no
  connected-invalid fallback, zero normals, unknown/blank mapped type, NaN /
  stair&lt;slab angles, column Top-wins / slope surface-only smoke.
- `DirectionalMappingLanguageContractTest` — V36 inventory retained.
