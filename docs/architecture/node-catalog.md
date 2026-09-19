# Build-time NodeCatalog

Design freeze for Phase C registration path. Replaces runtime classpath scanning as the
**primary** registration path for built-in nodes.

## Status

**Registration pipeline is good — do not overhaul it.**

```
@NodeInfo sources
    → generateNodeCatalog (Gradle)
    → GeneratedNodeCatalog.java
    → NodeCatalog / DefaultNodeProvider
    → NodeRegistry
```

`AutoNodeScanner` remains a **recovery fallback** only when catalog registration
returns `0`. That is intentional and sufficient.

Display caches (icon resolution + sorted category/node lists) landed separately —
see `docs/architecture/node-library-display-cache.md`. Leave those alone unless
broken.

## Resolutions (registration — frozen)

| Topic | Decision |
|-------|----------|
| Artifact | Generated Java `GeneratedNodeCatalog` under `build/generated/sources/nodeCatalog` |
| Inputs | `src/main/java/com/nodecraft/nodesystem/nodes/**/*.java` |
| Metadata | `@NodeInfo` fields (`id`, `displayName`, `description`, `category`, `order`, `icon`); convention fallback matches `AutoNodeScanner` |
| Deprecated | Class-level `@Deprecated` nodes are omitted (same as `NodeRegistry.shouldSkipRegistration`) |
| Nested classes | Not scanned from source files (top-level `*Node.java` only) |
| SPI | Unchanged — third-party `INodeProvider`s still load via `ServiceLoader` |
| Scanner | Fallback only when catalog registration returns `0` |
| Validation | Build-time does **not** instantiate nodes; `NodeContractTest` / catalog contract cover id/`typeId`/ports |
| APT | Deferred — Gradle source scan is enough for this slice |

## Exit gates (Phase C — met)

1. `./gradlew generateNodeCatalog compileJava` succeeds
2. Prod path registers from `GeneratedNodeCatalog` (scanner not used when count &gt; 0)
3. `NodeCatalogContractTest` + `NodeContractTest` green
4. Advancement Phase C marked PASS

---

## Next: unified catalog codegen

### U0 — Shared catalog model (done)

- `gradle/node-catalog-model.gradle` — collect / validate / JSON helpers
- `generateNodeCatalog` emits:
  - `build/generated/nodeCatalog/node-catalog.json` (shared SoT)
  - `GeneratedNodeCatalog.java` (runtime registration, unchanged role)
- `verifyNodeCatalogModel` (wired into `check`) asserts JSON ↔ Java IDS

### Remaining slices

Today other “node lists” still diverge until U1+:

| Consumer | How it gets data today | Drift risk |
|----------|------------------------|------------|
| `GeneratedNodeCatalog` | Gradle `@NodeInfo` scan via shared model | SoT for registration |
| `docs/NODE_LIBRARY.md` / `.zh-CN.md` | `generateNodeLibraryDocs` from JSON | Low (overwrite on generate) |
| AI node schema | Build stub from JSON; full ports/params still runtime `AiNodeSchemaCatalog` | Low (ids); Medium (ports) |
| Icon manifest | `generateNodeCatalogManifests` from JSON + asset scan | Low |
| Compatibility / alias manifest | Cross-check migration `nodeTypes` against catalog ids | Low (report missing targets) |
| Coverage report | `generateNodeCatalogCoverageReport` (+ CI artifact) | Low |

**Goal:** the JSON model fans out to every artifact so there is **one** node inventory.

```
@NodeInfo sources
    → collect catalog model (shared) → node-catalog.json
        → GeneratedNodeCatalog.java     (runtime register — exists)
        → NODE_LIBRARY.md               (docs)          [U1]
        → NODE_LIBRARY.zh-CN.md         (docs)          [U1]
        → AI node schema (JSON)         (build-time)    [U2]
        → icon manifest                 (id → icon key) [U3]
        → compatibility manifest        (aliases)       [U3]
        → node coverage report          (counts/gaps)   [U4]
```

### Non-goals for this follow-up

- Changing `DefaultNodeProvider` / `NodeRegistry` SPI wiring
- Removing `AutoNodeScanner` fallback
- Annotation-processor rewrite (keep Gradle scan unless APT becomes clearly cheaper)
- Re-litigating display/icon cache design

### Suggested slices

| Slice | Deliverable | Status |
|-------|-------------|--------|
| **U0** | Shared catalog model + `node-catalog.json` | **done** |
| **U1** | Generate `NODE_LIBRARY.md` + `.zh-CN.md` from the model | **done** |
| **U2** | Build-time AI schema stub (ids + display + category) | **done** |
| **U3** | Icon + compatibility manifests | **done** |
| **U4** | Coverage report task wired into CI | **done** |

### U1 notes

- Task: `generateNodeLibraryDocs` (also `finalizedBy` from `generateNodeCatalog`)
- Outputs: `docs/NODE_LIBRARY.md`, `docs/NODE_LIBRARY.zh-CN.md`
- `verifyNodeCatalogModel` asserts every catalog id appears in both docs
- Hand edits to those markdown files will be overwritten on the next generate

### U2 notes

- Task: `generateAiNodeSchemaStub` (also `finalizedBy` from `generateNodeCatalog`)
- Output: `build/generated/nodeCatalog/ai-node-schema-stub.json`
- Fields: `typeId`, `displayName`, `description`, `category` (+ `schemaKind=catalog-stub`, revision)
- Does **not** replace runtime `AiNodeSchemaCatalog` / `AiNodeSchemaExporter` (ports/params still need instantiation)
- `verifyNodeCatalogModel` asserts stub typeIds match catalog entry ids (order-sensitive) and revision is stable

### U3 notes

- Task: `generateNodeCatalogManifests` (also `finalizedBy` from `generateNodeCatalog`)
- Outputs:
  - `build/generated/nodeCatalog/node-icon-manifest.json` — id → explicit icon + derived paths (mirrors `NodeIconPathResolver`) + SVG existence under `assets/nodecraft`
  - `build/generated/nodeCatalog/node-compatibility-manifest.json` — V0→V1 `nodeTypes` aliases with `targetInCatalog` / `missingTargets`
- Does **not** rewrite `src/main/resources/nodecraft/migration/v0-to-v1.json` (still owned by file-migration / `scripts/build_v0_migration_manifest.py`)
- Does **not** change runtime icon resolution caches
- `verifyNodeCatalogModel` asserts icon ids match catalog and alias pairs match migration × catalog cross-check

### U4 notes

- Task: `generateNodeCatalogCoverageReport` (also `finalizedBy` from `generateNodeCatalog`; required by `verifyNodeCatalogModel` / `check`)
- Outputs: `build/generated/nodeCatalog/node-coverage-report.json` + `.md`
- Aggregates description / icon SVG / migration-target gaps; does **not** fail on incomplete coverage
- CI (`.github/workflows/ci.yml`) prints the markdown and uploads both files as `node-catalog-coverage`

Contract: `verifyNodeCatalogModel` keeps `GeneratedNodeCatalog.IDS` equal to JSON entry ids,
docs coverage, AI stub typeIds, icon/compat manifests, and coverage report consistency.

## Related

- `docs/contracts/node-metadata.md`
- `docs/architecture/node-library-display-cache.md`
- `gradle/node-catalog.gradle`
- `DefaultNodeProvider`, `NodeCatalog`, `GeneratedNodeCatalog`
