# Graph format version

Development-stage policy: **current format only**. No historical migration ladder.

## Canonical API

`com.nodecraft.nodesystem.io.GraphFormatVersion`

| Constant | Value | Meaning |
|----------|-------|---------|
| `UNSPECIFIED` | `0` | Pre-versioning / omitted field |
| `CURRENT` | `1` | Version written by current builds |

Historical `V2`…`V135` step constants and remaps were removed. Language docs may still mention prior
batch names for narrative history; on-disk behavior is stamp-only.

## Load policy

```
normalize(version) = version <= 0 ? UNSPECIFIED : version
if version > CURRENT → load best-effort, no stamp (warn)
if version < CURRENT → normalize structure, stamp formatVersion = CURRENT (no port/type remaps)
if version == CURRENT → load as-is
```

New saves always write `formatVersion = CURRENT`.

## Ownership

`GraphMigrationRegistry.migrateToCurrent(SavedGraph)` only stamps older payloads to `CURRENT`.
There is no `v0-to-v1.json` migration manifest.

## Related

- Contract: [`docs/contracts/graph-format.md`](../contracts/graph-format.md)
- Tests: `GraphFormatVersionContractTest`, `GraphMigrationRegistryTest`
