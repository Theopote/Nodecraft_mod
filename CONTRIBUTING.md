# Contributing to NodeCraft

NodeCraft is still under active development. Keep changes focused, verify them locally, and avoid preserving obsolete runtime compatibility unless a migration path requires it.

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

## Preset Changes

Built-in graph presets must pass `GraphPresetResourceTest`. That test verifies:

- every preset node type exists in `NodeRegistry`;
- every preset connection references real input/output ports;
- every connected port pair is type compatible.

If old preset source files need migration, update converter tooling and regenerate clean resources.
