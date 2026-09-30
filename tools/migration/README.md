# Migration tooling

Canonical paths only — do **not** add one-shot `fix_*.py` / `run_converter_*.bat`
scripts back to the repo root.

## Use these

| Task | Command / path |
|------|----------------|
| Preset → `graph_presets.json` conversion | `./gradlew runPresetConverter` |
| Preset resource contract | `GraphPresetResourceTest` |
| Graph format on load | `GraphMigrationRegistry` stamps older payloads to `CURRENT` (no remaps) |
| Canonicalize on-disk `presets/**/preset.json` ids | `python scripts/canonicalize_presets.py` |
| Node library docs | `./gradlew generateNodeLibraryDocs` (from `node-catalog.json`) |

## Do not use

Historical root scripts such as `fix_node_ids.py`, `run_converter_final.bat`,
`validate_presets.py`, `build_v0_migration_manifest.py`, etc. were removed or retired.
Their war stories live under `docs/history/` and are **not** current procedure.

There is **no** maintained `v0-to-v1.json` migration ladder during active development.
