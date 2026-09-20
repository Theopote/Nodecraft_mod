# Contributing to NodeCraft

NodeCraft is still under active development. Keep changes focused, verify them locally, and avoid preserving obsolete runtime compatibility unless a migration path requires it.

## Node ids and library docs

Canonical node ids and categories come from `@NodeInfo` in code and the generated
[`docs/NODE_LIBRARY.md`](docs/NODE_LIBRARY.md) (catalog codegen). Do not treat older
taxonomy drafts (`node-id-guidelines`, v1 category tree, uploaded “节点库介绍”, etc.) as
current specs — see [`docs/architecture/docs-authority.md`](docs/architecture/docs-authority.md).
Legacy ids belong on the graph migration path only.

When adding or remodeling nodes, follow the V1 language freeze:
[`docs/nodecraft-v1-node-language.md`](docs/nodecraft-v1-node-language.md)
(continuous scalars as `DOUBLE`, stable port ids, degrees for angles,
Point / Vector / Block Position, property fallback + port override).

## Local Checks

Run a compile check before handing off Java changes:

```powershell
.\gradlew --no-daemon --console plain compileJava
```

Run focused tests for the subsystem you changed. Useful examples:

```powershell
.\gradlew --no-daemon --console plain test --tests com.nodecraft.gui.preset.GraphPresetResourceTest
.\gradlew --no-daemon --console plain test --tests com.nodecraft.gui.layout.StandardLayoutManagerTest
```

Run the full test suite for shared behavior:

```powershell
.\gradlew --no-daemon --console plain test
```

## Code Quality

- Use `NodeCraft.LOGGER` or an SLF4J logger instead of `System.out`, `System.err`, or `printStackTrace()`.
- Remove TODOs when the code path is implemented. If a task is intentionally deferred, document it in `docs/` rather than leaving vague inline comments.
- Keep preset runtime behavior strict. Fix or migrate `graph_presets.json` rather than adding broad runtime aliases.
- Prefer existing node, graph, execution, and UI patterns over new abstractions.
- Add tests when changing shared behavior, preset resources, layout, execution, serialization, or type compatibility.

## Minecraft Version Upgrades

When bumping Minecraft, Yarn mappings, Fabric Loader/API, or Loom:

1. Run the compile and test gates above.
2. Follow the mixin regression checklist in [`docs/minecraft-upgrade-mixin-checklist.md`](docs/minecraft-upgrade-mixin-checklist.md).

Pay special attention to input API mixins (`Mouse`, `Keyboard`, `KeyboardInput`) and render hooks (`RenderSystem`, `WorldRenderer`).

## Preset Changes

Built-in graph presets must pass `GraphPresetResourceTest`. That test verifies:

- every preset node type exists in `NodeRegistry`;
- every preset connection references real input/output ports;
- every connected port pair is type compatible.

If old preset source files need migration, update converter tooling and regenerate clean resources.
