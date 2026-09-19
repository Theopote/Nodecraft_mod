# Graph format contracts

Invariant fence for `SavedGraph.formatVersion`.

## Rules

| Contract | Rule |
|----------|------|
| Current identity | `GraphFormatVersion.CURRENT == GraphFormatVersion.V1 == 1` |
| Legacy floor | `V0 == LEGACY_UNSPECIFIED == 0` |
| Normalize | `normalize(v) == max(v, V0)` |
| Needs migration | `normalize(v) < CURRENT` |
| Newer | `v > CURRENT` |
| Save path | `GraphSerializer.toSavedGraph` writes `CURRENT` |
| Load path | Legacy payloads migrate to `CURRENT` via `GraphMigrationRegistry` + `v0-to-v1.json` manifest |

## Suites

- `com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest`
- `com.nodecraft.nodesystem.graph.GraphMigrationRegistryTest`
- `com.nodecraft.nodesystem.contract.LegacyGraphLoadContractTest`
- Existing: `GraphSerializerTest`, `SavedGraphNormalizerTest`

## Running

```bash
./gradlew test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
./gradlew test --tests "com.nodecraft.nodesystem.contract.LegacyGraphLoadContractTest"
```
