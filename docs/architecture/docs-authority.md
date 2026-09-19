# Documentation authority (taxonomy & node ids)

Design freeze: which docs may drive implementation vs which are design history.

## Problem

Older NodeCraft docs still describe a pre-migration taxonomy, for example:

| Historical | Current (code + generated library) |
|------------|-------------------------------------|
| `visualization.preview.*` | `output.preview.*` |
| `visualization.execute.*` | `output.execute.*` |
| `spatial.construct` / `spatial.modeling` / `spatial.voxel` / … | `geometry.*` |
| `visualization.preview.geometry_viewer` | `output.preview.geometry_viewer` |

Using those older docs as implementation specs causes wrong ids, wrong categories, and
false “missing node” reports. This is exactly the docs side of **0.9 Compatibility**.

## Source of truth (frozen)

When reviewing or implementing NodeCraft nodes / graphs / presets / AI schema:

1. **Current GitHub code** — especially `@NodeInfo(id = …)` under
   `src/main/java/com/nodecraft/nodesystem/nodes/**`
2. **Generated library docs** — `docs/NODE_LIBRARY.md` and `docs/NODE_LIBRARY.zh-CN.md`
   (from `node-catalog.json` via `generateNodeLibraryDocs`; do not hand-edit)

Supporting SoT for registration / inventory:

- `docs/architecture/node-catalog.md` — catalog codegen pipeline
- `build/generated/nodeCatalog/node-catalog.json` — shared inventory artifact

If a prose doc disagrees with code or `NODE_LIBRARY.md`, **the code / library win**.

## Historical / design-reference only

Treat the following as **migration-era design history**, not current implementation norms:

| Doc | Why demoted |
|-----|-------------|
| `docs/node-id-guidelines.md` | Written for an earlier “canonical-only, no aliases” moment; taxonomy examples and registration claims drift from catalog + migration |
| `docs/nodecraft-v1-category-id-guidelines.md` | Category scheme draft; live categories come from catalog / `NODE_LIBRARY` |
| `docs/NodeCraft-v1.0-节点分类树-定稿.md` | Classification tree draft from the v1 rename |
| `docs/nodecraft-v1-node-alias-plan.md` | Alias **plan** used to seed V0→V1 migration; not a live taxonomy |
| `docs/nodecraft-v1-current-state.md` | Point-in-time status |
| `docs/Bake改进路线图.md` and other notes still citing `spatial.*` / `visualization.*` as homes | Pre-rename bake / voxel language |
| External / uploaded “节点系统文档”、“节点库介绍” copies | Same drift; prefer repo `NODE_LIBRARY*.md` |

They remain useful to understand **why** ids moved. They must not be used to decide
what a node’s id or category is today.

## Compatibility ownership (0.9)

Legacy ids (`visualization.*`, `spatial.*`, …) are **not** current runtime homes.

| Concern | Owner |
|---------|--------|
| New nodes / docs / presets | Canonical ids only (`geometry.*`, `output.*`, `world.*`, …) |
| Loading old graphs / presets | `GraphFormatVersion` + `GraphMigrationRegistry` + V0→V1 manifest |
| Cross-check alias targets | Catalog compatibility manifest (`node-catalog.md` U3) |

Do **not** reintroduce broad runtime alias tables in `NodeRegistry` for taxonomy.
File migration owns legacy ids (see `docs/architecture/graph-format-version.md`).

## Working rule

1. Need a node id? → search `NODE_LIBRARY.md` or `@NodeInfo` / catalog JSON.
2. Need rename history? → migration manifest / alias plan (history).
3. Need product taxonomy intent? → historical category docs OK as background only.
4. Never “fix” current code to match an old uploaded doc without verifying catalog.

## Exit

- This freeze is linked from `docs/development/README.md` and `docs/history/README.md`
- Stale guideline docs carry a supersession banner pointing here
- Advancement 0.9 notes docs-authority as part of Compatibility follow-through
