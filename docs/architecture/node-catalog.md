# Build-time NodeCatalog

Design freeze for Phase C. Replaces runtime classpath scanning as the **primary** registration path for built-in nodes.

## Goal

Emit a deterministic catalog of built-in node metadata at build time, then register from that catalog at startup.

```
@NodeInfo sources
    → generateNodeCatalog (Gradle)
    → GeneratedNodeCatalog.java
    → DefaultNodeProvider.registerNodes
    → NodeRegistry
```

## Resolutions

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

## Non-goals (this slice)

- Feeding AI schema / `NODE_LIBRARY.md` / compatibility manifests from the same pipeline (follow-up)
- Annotation processor module
- Removing `AutoNodeScanner` source (kept as recovery fallback)
- Icon / display caches (Phase E — landed)

## Exit gates

1. `./gradlew generateNodeCatalog compileJava` succeeds
2. Prod path registers from `GeneratedNodeCatalog` (scanner not used when count &gt; 0)
3. `NodeCatalogContractTest` + `NodeContractTest` green
4. Advancement Phase C marked PASS
