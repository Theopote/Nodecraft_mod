# Graph format contracts

Invariant fence for `SavedGraph.formatVersion` (development: current-only, no historical remaps).

## Rules

| Contract | Rule |
|----------|------|
| Current identity | `GraphFormatVersion.CURRENT == 1` |
| Unspecified floor | `UNSPECIFIED == 0` |
| Normalize | `normalize(v) == UNSPECIFIED` when `v <= 0`, else `v` |
| Needs migration | `normalize(v) < CURRENT` (stamp only) |
| Newer | `v > CURRENT` |
| Save path | `GraphSerializer.toSavedGraph` writes `CURRENT` |
| Load path | Older payloads are stamped to `CURRENT` without port/type remaps |

## Suites

- `com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest`
- `com.nodecraft.nodesystem.graph.GraphMigrationRegistryTest`
- Existing: `GraphSerializerTest`, `SavedGraphNormalizerTest`

## Running

```bash
./gradlew test --tests "com.nodecraft.nodesystem.contract.GraphFormatVersionContractTest" --tests "com.nodecraft.nodesystem.graph.GraphMigrationRegistryTest"
```
