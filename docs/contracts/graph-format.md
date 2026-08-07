# Graph format contracts

Invariant fence for `SavedGraph.formatVersion`.

## Rules

| Contract | Rule |
|----------|------|
| Current identity | `GraphFormatVersion.CURRENT == GraphFormatVersion.V1 == 1` |
| Legacy floor | `LEGACY_UNSPECIFIED == 0` |
| Normalize | `normalize(v) == max(v, LEGACY_UNSPECIFIED)` |
| Needs migration | `normalize(v) < CURRENT` |
| Newer | `v > CURRENT` |
| Save path | `GraphSerializer.toSavedGraph` writes `CURRENT` |
| Load path | Legacy payloads migrate to `CURRENT` via `GraphMigrationRegistry` |

## Suites

- `com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest`
- Existing: `GraphMigrationRegistryTest`, `GraphSerializerTest`

## Running

```bash
./gradlew test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest"
```
