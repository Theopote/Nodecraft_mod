# Node Language v2 — Type Selectors

**Status: PASSED / FROZEN** (Graph **V113**; V33 remains historical v1)

Freeze hardening for `input.type_selectors.*`: Graph `Valid` is Identifier **syntax** plus
Allow Modded policy. Runtime registry membership stays downstream (PlacementPreflight / Apply /
Export / world reads). Picker catalogs are shared via `RegistryCatalogCache` and never prove
`Valid`.

Related: [`node-language-v1-type-selectors.md`](./node-language-v1-type-selectors.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Valid contract

```text
Valid = Identifier.tryParse(canonicalId) succeeds
     && (allowModded || namespace == minecraft)
```

| Condition | Primary output | `Valid` |
|-----------|----------------|---------|
| Well-formed id + policy ok | canonical id | `true` |
| Well-formed unknown / missing mod id + `allowModded=true` | preserved id | `true` |
| Non-authoritative biome UI fallback + well-formed id | preserved id | `true` |
| Modded id + `allowModded=false` | preserved id | `false` |
| Invalid syntax on restore | default id (display) | `false` |

Authoritative registry membership is **not** required for Graph `Valid`.

## Catalog vs validation

| Surface | Role |
|---------|------|
| `RegistryCatalog(ids, authoritative)` | Picker list + editor convenience flag |
| `RegistryCatalogCache` | Shared immutable sorted lists per kind / authoritative key |
| Biome live world/network registry | Prefer for picker when available |
| `FALLBACK_BIOME_IDS` / quick picks | UI suggestions only |
| `isKnownBiomeId` | Live registry lookup for UI “known” hints — not `output_valid` |
| Block / Item / Entity `Registries.*` | Picker source when non-empty |

## Filter policy

| Control | Scope |
|---------|-------|
| Search / category / `minecraftOnly` | **UI-only** — picker list; never rewrite saved id |
| `Allow Modded` | **Validation + picker** — modded id with `allowModded=false` → `Valid=false`, id preserved |

## Status history

| Graph | Role |
|-------|------|
| **V33** | Historical v1: `output_valid`, `BLOCK_TYPE`, Block State Selector removal |
| **V113** | Stamp-only fence; freeze: Valid = syntax (+ allowModded); shared catalog cache |

## Migration

Graph **V112→V113** is a no-op (Valid semantics; no wire remaps). Stamp-only migrator residue.

## Contract

- `TypeSelectorsLanguageV2ContractTest` — V113 fence, syntax Valid, non-authoritative biome may be
  Valid, allowModded policy, unknown well-formed mod id.
- `TypeSelectorsLanguageContractTest` — V33 inventory / PURE / migration retained.
