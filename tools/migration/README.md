# Migration tooling

Canonical paths only — do **not** add one-shot `fix_*.py` / `run_converter_*.bat`
scripts back to the repo root.

## Use these

| Task | Command / path |
|------|----------------|
| Preset → `graph_presets.json` conversion | `./gradlew runPresetConverter` |
| Preset resource contract | `GraphPresetResourceTest` |
| Graph id / port migration (load path) | `GraphMigrationRegistry` + `src/main/resources/nodecraft/migration/v0-to-v1.json` |
| Rebuild V0→V1 migration manifest | `python scripts/build_v0_migration_manifest.py` |
| Canonicalize on-disk `presets/**/preset.json` ids | `python scripts/canonicalize_presets.py` |
| Node library docs | `./gradlew generateNodeLibraryDocs` (from `node-catalog.json`) |

## Do not use

Historical root scripts such as `fix_node_ids.py`, `run_converter_final.bat`,
`validate_presets.py`, etc. were removed. Their war stories live under
`docs/history/` and are **not** current procedure.
