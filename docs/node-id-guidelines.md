# Node ID and Category Guidelines

> **Superseded as implementation SoT.**  
> Current node ids and categories come from **GitHub code** (`@NodeInfo`) and generated
> [`NODE_LIBRARY.md`](./NODE_LIBRARY.md) / [`NODE_LIBRARY.zh-CN.md`](./NODE_LIBRARY.zh-CN.md).  
> Authority freeze: [`architecture/docs-authority.md`](./architecture/docs-authority.md).  
> Keep this file as **design history** for the v1 taxonomy rename (`visualization.*` /
> `spatial.*` → `output.*` / `geometry.*`, …). Do not use it to decide today’s ids.

Last updated: 2026-04-12 (historical)

## Scope

This document defined rules for the v1 taxonomy migration era:

- canonical node ids
- canonical category placement
- canonical-only registration and persistence behavior

The system no longer keeps removed cold-storage or legacy source trees in active code.
**Live inventory:** regenerate / read `NODE_LIBRARY.md` — do not treat the lists below as complete.

## Canonical Taxonomy

Canonical nodes must live under the v1 taxonomy:

- `input`
- `reference`
- `geometry`
- `transform`
- `pattern`
- `material`
- `world`
- `output`
- `math`

These are the only top-level domains that should be used for new node work.

## ID Rules

- Use lowercase dotted ids such as `geometry.solids.extrude`.
- Use `snake_case` for the leaf name.
- `@NodeInfo(id = ...)` and the `BaseNode` type id must always match.
- Package path, category, and id should point to the same semantic home.
- When a canonical category segment is a Java keyword, keep the canonical id/category unchanged and use the nearest clear non-keyword implementation package name instead.

## Category Rules

- A node has one canonical home.
- Do not create new semantic nodes under old domains such as:
  - `spatial.*`
  - `visualization.*`
  - `inputs.*`
  - `data.*`
  - `control.*`
  - `utilities.*`
- `utilities.assist`, `utilities.organization`, and `utilities.morphology` are utility/tooling areas, not broad modeling taxonomy extensions.

## Canonical-Only Rule

This repository is still in pre-release development.

- Do not keep old-to-new node id aliases in runtime code.
- Do not preserve removed taxonomy names in loaders, clipboard paths, or editor fallback logic.
- If a node is renamed or moved, update the codebase and test data directly instead of adding compatibility bridges.
- Old ids such as `visualization.*`, `inputs.*`, `world.modification.*`, `math.basic.*`, `math.randomness.*`, and `logic.*` are not supported runtime identifiers.

## Save and Load Rules

- Saving must write canonical ids.
- Clipboard export must write canonical ids.
- History snapshots must store canonical ids.
- Loading should expect canonical ids only.

## Registration Rules

- Annotation scanning is the primary registration path.
- `DefaultNodeProvider` should only register real top-level and fallback canonical categories.
- Do not keep placeholder categories or fallback logic for removed taxonomy names.

## Migration Checklist

When renaming or moving a node:

1. Move the class to the correct package.
2. Update `@NodeInfo(id = ...)`.
3. Update the `BaseNode` type id.
4. Update `NodeLibraryComponent` ordering if the node is explicitly ordered.
5. Update any local test data or editor defaults that still reference the old id.
6. Run `.\gradlew.bat compileJava --no-daemon`.

## Source of Truth

- taxonomy behavior:
  - [NodeRegistry.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java)
- library ordering and category presentation:
  - [NodeLibraryComponent.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/gui/components/NodeLibraryComponent.java)
- built-in category registration:
  - [DefaultNodeProvider.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/core/DefaultNodeProvider.java)
