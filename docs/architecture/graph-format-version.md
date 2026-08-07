# Graph format version

Design freeze for Phase I — on-disk / embedded `SavedGraph` compatibility.

## Canonical API

`com.nodecraft.nodesystem.io.GraphFormatVersion`

| Constant | Value | Meaning |
|----------|-------|---------|
| `LEGACY_UNSPECIFIED` | `0` | Pre-versioning JSON / omitted field |
| `V1` / `CURRENT` | `1` | Explicit version + migration registry |

`GraphFormat` remains a deprecated alias that forwards to these constants.

## Load policy

```
normalize(version) = max(version, LEGACY_UNSPECIFIED)
if version > CURRENT → load best-effort, no migration (warn)
if version < CURRENT → run GraphMigrationRegistry step-by-step until CURRENT
if version == CURRENT → load as-is
```

New saves always write `formatVersion = CURRENT`.

## Migration ownership

`GraphMigrationRegistry.migrateToCurrent(SavedGraph)` is the only place that bumps versions.
Add node/port aliases there when renaming catalog IDs — do not silently rely on runtime registry aliases alone for old files.

## Compatibility rules (frozen for 0.8)

1. Readers **must** accept `LEGACY_UNSPECIFIED` and migrate to `CURRENT`.
2. Writers **must** emit `CURRENT`.
3. Newer-than-current files are **not** rewritten; warn and load best-effort.
4. Bumping `CURRENT` requires a new `migrateStep` case and contract test.

## Non-goals

- Binary formats / protobuf
- Network multiplayer graph sync
- Auto-rewriting future-version files down to CURRENT

## Exit gates

1. `GraphFormatVersion` + policy helpers land
2. Contract / migration tests green
3. Advancement Phase I PASS; 0.8 Interactive Runtime checklist complete
