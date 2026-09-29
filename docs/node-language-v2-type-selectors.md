# Node Language v2 — Type Selectors

**Status: PASSED / FROZEN** (Graph **V113**; V33 remains historical v1)

Authoritative-registry remediation for `input.type_selectors.*`: UI fallback biome catalogs
must never prove Graph `Valid`. Search / category / `minecraftOnly` stay UI-only;
`Allow Modded` is both picker policy and validation policy.

Related: [`node-language-v1-type-selectors.md`](./node-language-v1-type-selectors.md),
[`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md).

## Valid contract

```text
Valid = syntaxOK
     && registryAuthoritative
     && registryContains(id)
     && (allowModded || namespace == minecraft)
```

| Condition | Primary output | `Valid` |
|-----------|----------------|---------|
| Authoritative registry + known id + policy ok | canonical id | `true` |
| Biome catalog is UI fallback only | preserved id | `false` |
| Registry unavailable / empty | preserved id | `false` |
| Modded id + `allowModded=false` | preserved id | `false` |
| Unknown / missing mod id | preserved id | `false` |

## Catalog vs validation

| Surface | Role |
|---------|------|
| `RegistryCatalog(ids, authoritative)` | Picker list + authority flag |
| Biome live world/network registry | `authoritative=true` |
| `FALLBACK_BIOME_IDS` / quick picks | UI suggestions only (`authoritative=false`) |
| `isKnownBiomeId` | Live registry lookup only — never scans fallbacks |
| Block / Item / Entity `Registries.*` | Authoritative when non-empty |

## Filter policy

| Control | Scope |
|---------|-------|
| Search / category / `minecraftOnly` | **UI-only** — picker list; never rewrite saved id |
| `Allow Modded` | **Validation + picker** — modded id with `allowModded=false` → `Valid=false`, id preserved |

## Status history

| Graph | Role |
|-------|------|
| **V33** | Historical v1: `output_valid`, `BLOCK_TYPE`, Block State Selector removal |
| **V113** | Remediation: non-authoritative biome fallback → `Valid=false` |

## Migration

Graph **V112→V113** is a no-op (Valid semantics; no wire remaps).

## Contract

- `TypeSelectorsLanguageV2ContractTest` — V113 fence, `isKnownBiomeId`, non-authoritative biome,
  `computeValid(authoritative)`, allowModded policy.
- `TypeSelectorsLanguageContractTest` — V33 inventory / PURE / migration retained.
