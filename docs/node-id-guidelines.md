# Node ID and Category Guidelines

Last updated: 2026-04-12

## Scope

This document defines the current rules for:

- canonical node ids
- canonical category placement
- the limited alias policy that is still allowed

The system no longer keeps removed cold-storage or legacy source trees in active code.

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

## Category Rules

- A node has one canonical home.
- Do not create new semantic nodes under old domains such as:
  - `spatial.*`
  - `visualization.*`
  - `inputs.*`
  - `data.*`
  - `control.*`
  - `utilities.*`
- `utilities.assist` and `utilities.organization` are editor-side helpers, not modeling taxonomy extensions.

## Alias Policy

Aliases are now intentionally narrow.

Keep aliases only when all of the following are true:

1. The old id was widely used during this refactor.
2. The new target is a real canonical v1 node.
3. The alias helps bridge a direct rename, not an entire removed subsystem.

Examples of acceptable alias families:

- `visualization.* -> output.*`
- `world.query/world.modification -> world.read/world.write`
- `inputs.basic/inputs.minecraft -> input.* / world.* / reference.*`
- `math.basic/math.randomness/math.vector -> math.* / reference.* / transform.*`

Examples that should not be reintroduced:

- removed cold-storage buckets
- removed legacy compatibility buckets
- broad `spatial.*` compatibility nets
- broad `utilities.*`, `data.*`, or `control.flow.*` fallback systems

## Save and Load Rules

- Saving must write canonical ids.
- Clipboard export must write canonical ids.
- History snapshots must store canonical ids.
- Alias resolution is only for loading or interpreting older ids that are still explicitly supported.

## Registration Rules

- Annotation scanning is the primary registration path.
- `DefaultNodeProvider` should only register real top-level and fallback canonical categories.
- Do not keep placeholder compatibility categories alive once their source trees are gone.

## Migration Checklist

When renaming or moving a node:

1. Move the class to the correct package.
2. Update `@NodeInfo(id = ...)`.
3. Update the `BaseNode` type id.
4. Update `NodeLibraryComponent` ordering if the node is explicitly ordered.
5. Add an alias only if the rename meets the narrow alias policy.
6. Run `.\gradlew.bat compileJava --no-daemon`.

## Source of Truth

- taxonomy and alias behavior:
  - [NodeRegistry.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/registry/NodeRegistry.java)
- library ordering and category presentation:
  - [NodeLibraryComponent.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/gui/components/NodeLibraryComponent.java)
- built-in category registration:
  - [DefaultNodeProvider.java](/f:/development/NC/nodecraft/src/main/java/com/nodecraft/nodesystem/core/DefaultNodeProvider.java)
