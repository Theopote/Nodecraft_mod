# Node ID and Category Guidelines

This document defines the current rules for node ids, category placement, alias compatibility, and legacy boundaries.

## Goals

- Keep package path, category, and node id aligned.
- Preserve old graphs, clipboard data, and history records through alias resolution.
- Ensure any asset re-save writes canonical ids instead of legacy ids.
- Prevent new nodes from reintroducing `spatial.generators.*` history debt.

## Canonical Category Rules

- Canonical ids must use the v1 taxonomy, not the pre-v1 taxonomy.
- Current canonical top-level domains are:
  - `input`
  - `reference`
  - `geometry`
  - `transform`
  - `pattern`
  - `material`
  - `world`
  - `output`
  - `math`
- Old semantic domains such as `spatial.construct`, `spatial.analysis`, `spatial.modeling`, `spatial.instancing`, `spatial.voxel`, and `visualization.*` are no longer canonical homes.
- `spatial.legacy` is a display-only compatibility bucket used by the library for deferred and legacy nodes that still need to load safely.

## Canonical ID Rules

- Canonical ids must match the node's semantic category.
  - Good: `spatial.construct.sphere_by_center_radius`
  - Bad: `spatial.generators.sphere_by_center_radius`
- Use lowercase snake case for the leaf name.
  - Good: `torus_blocks`
  - Bad: `torusblocks`
- If a node is physically moved to a new package and category, its canonical id must move too.
- `BaseNode` type ids and `@NodeInfo(id = ...)` must always match the same canonical id.

## Legacy Rules

- `spatial.legacy` is not a canonical authoring taxonomy. It is a compatibility presentation layer.
- Nodes may still keep old ids and packages temporarily while appearing under `spatial.legacy` in the library through category overrides.
- Current legacy buckets include:
  - `spatial.generators.*_blocks`
  - deferred `spatial.analysis.*` survivors
  - deferred `spatial.instancing.*` survivors
  - deferred `spatial.voxel.*` survivors
  - deferred `inputs.minecraft.*` survivors
- Do not add new semantic modeling, construct, analysis, or world-input nodes under old domains just because `spatial.legacy` can display them.
- Do not add new non-legacy nodes under `spatial.generators`.

## Alias Rules

- Old ids must be preserved through `NodeRegistry` aliases when a node's canonical id changes.
- There are two supported alias patterns:
  - Explicit moved-node aliases
    - Example: `spatial.generators.sphere_by_center_radius -> spatial.construct.sphere_by_center_radius`
  - Compact legacy aliases
    - Example: `spatial.generators.torusblocks -> spatial.generators.torus_blocks`
- Aliases are maintained in:
  - `src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java`

## Asset Compatibility Rules

- Loading must accept legacy ids through `NodeRegistry.createNodeInstance(...)`.
- Saving, clipboard export, history snapshots, and duplicate-node flows must write canonical ids.
- The current canonical write paths include:
  - `src/main/java/com/nodecraft/nodesystem/graph/GraphSerializer.java`
  - `src/main/java/com/nodecraft/gui/editor/impl/ImGuiNodeIO.java`
  - `src/main/java/com/nodecraft/gui/editor/impl/ImGuiNodeClipboard.java`
  - `src/main/java/com/nodecraft/gui/editor/impl/ImGuiNodeHistory.java`
  - `src/main/java/com/nodecraft/gui/editor/impl/ImGuiNodeMenus.java`

## Registration Rules

- The primary registration path is annotation scanning via:
  - `src/main/java/com/nodecraft/nodesystem/core/DefaultNodeProvider.java`
  - `src/main/java/com/nodecraft/nodesystem/core/AutoNodeScanner.java`
- Manual helper registrars should be considered compatibility-only, not the source of truth.
- If a manual registrar is still kept, mark it clearly as deprecated compatibility code.

## Migration Checklist

When moving or renaming a node:

1. Move the class to the correct package.
2. Update `@NodeInfo(id = ...)` to the canonical id.
3. Update the `BaseNode` constructor type id to the same canonical id.
4. Add old-to-new alias entries in `NodeRegistry`.
5. Update explicit library ordering in `NodeLibraryComponent` if that node is listed there.
6. Verify save/export paths will now write the canonical id.
7. Run `.\gradlew compileJava`.

## Do Not

- Do not create new semantic nodes under `spatial.generators`, `spatial.analysis`, `spatial.modeling`, `spatial.instancing`, `spatial.voxel`, or `inputs.minecraft`.
- Do not introduce new compact ids like `lineblocks` or `torusblocks` as canonical ids.
- Do not rely on category remapping as a long-term substitute for canonical id migration.

## Current Source of Truth

- Canonical ids and alias compatibility:
  - `src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java`
- Library category ordering and presentation:
  - `src/main/java/com/nodecraft/gui/components/NodeLibraryComponent.java`
- Main registration flow:
  - `src/main/java/com/nodecraft/nodesystem/core/DefaultNodeProvider.java`
