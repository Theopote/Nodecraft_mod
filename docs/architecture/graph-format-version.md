# Graph format version

Design freeze for on-disk / embedded `SavedGraph` compatibility.

## Canonical API

`com.nodecraft.nodesystem.io.GraphFormatVersion`

| Constant | Value | Meaning |
|----------|-------|---------|
| `V0` / `LEGACY_UNSPECIFIED` | `0` | Pre-versioning JSON / omitted field |
| `V1` | `1` | Explicit version + V0→V1 taxonomy migration manifest |
| `V2` / `CURRENT` | `2` | Batch A language remediation (Integer Slider `value` → `output_value`) |

## Load policy

```
normalize(version) = max(version, V0)
if version > CURRENT → load best-effort, no migration (warn)
if version < CURRENT → run GraphMigrationRegistry step-by-step until CURRENT
if version == CURRENT → load as-is
```

New saves always write `formatVersion = CURRENT`.

## Migration ownership

`GraphMigrationRegistry.migrateToCurrent(SavedGraph)` is the only place that bumps versions.

The V0→V1 manifest lives at:

- `src/main/resources/nodecraft/migration/v0-to-v1.json`
- generated/updated by `scripts/build_v0_migration_manifest.py` from `docs/nodecraft-v1-node-alias-plan.md`

It covers:

- node type rename (`visualization.*` → `output.*`, `spatial.*` → `geometry.*`, etc.)
- port rename (global + per-node overrides)
- node state property rename
- enum value rename (reserved for future rows)

V1→V2 is applied inline in `GraphMigrationRegistry.migrateV1ToV2`:

- `input.numeric.integer_slider` output port `value` → `output_value`

(See [`nodecraft-v1-node-language.md`](../nodecraft-v1-node-language.md) Batch A.)

`NodeRegistry.resolveCanonicalNodeId(...)` remains lowercase normalization only. **Do not** add runtime alias tables there; file migration owns legacy ids.

## Compatibility rules

1. Readers **must** accept `V0` and migrate to `CURRENT`.
2. Writers **must** emit `CURRENT`.
3. Newer-than-current files are **not** rewritten; warn and load best-effort.
4. Bumping `CURRENT` requires a new manifest step and contract/legacy fixture tests.

## Regression fixtures

Legacy graphs under `src/test/resources/legacy/v0/*.nodecraft` are loaded with the full node registry in `LegacyGraphLoadContractTest`.

## Exit gates

1. `GraphFormatVersion` + policy helpers land
2. `GraphMigrationRegistry` + manifest land
3. Contract + legacy fixture tests green
