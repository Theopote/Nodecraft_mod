# Execution Runtime 2.0 — Design Freeze

Parent roadmap: [`../development/advancement-0.7-to-0.8.md`](../development/advancement-0.7-to-0.8.md)

**Status:** Design freeze — Phase A (`v0.7-stability`) is PASS. Resolve open questions below before coding the vertical slice.

## Problem

`NodeExecutor` currently concentrates too many responsibilities:

- Graph execution session
- Thread lifecycle / pool
- Incremental scope
- Exec frontier
- World-thread routing
- Preview cleanup hooks
- Cache / error handling

Auto-preview already has dirty propagation + `IncrementalExecutionPlanner`, but many paths still spin a heavy executor lifecycle. That blocks Grasshopper/Houdini-style interactive modeling.

## Target architecture

```text
Graph (+ dirty set)
        │
        ▼
IncrementalExecutionPlanner / GraphExecutionPlanner
        │
        ▼
ExecutionPlan (immutable scope + mode: dataflow | exec | preview)
        │
        ▼
NodeExecutionScheduler   ◄── shared, long-lived (server/client as appropriate)
        │
        ├── Worker queue (compute nodes)
        ├── World-thread queue (mutations / bake / tracked preview writes)
        ├── Cancellation tokens
        ├── Priority (interactive preview > background)
        └── Session registry
                │
                ▼
        ExecutionSession (replaces “fat” NodeExecutor)
                - binds one graph run / preview generation
                - does not own the pool
```

## Proposed types (names can change; boundaries must not)

| Type | Responsibility |
|------|----------------|
| `NodeExecutionScheduler` | Queues, workers, world-thread pump, global cancel, metrics |
| `ExecutionSession` | One logical run: plan, node cursor, caches, result, cancel flag |
| `ExecutionPlan` | Immutable: node order / frontier seeds / incremental scope / options |
| `CancellationToken` | Session + scheduler cooperative cancel |
| `WorldWork` | Runnable that **must** run on Minecraft server thread |

`NodeExecutor` either becomes a thin facade over `ExecutionSession`, or is renamed and deleted after migration.

## Auto-preview path (must-win vertical slice)

```text
property / connection change
  → mark dirty
  → IncrementalExecutionPlanner.scope(dirty)
  → ExecutionPlan.preview(...)
  → scheduler.submit(session)
  → recompute affected subgraph only
  → TrackedPreviewPlacementService update
```

Constraints:

- No new dedicated thread pool per keystroke
- Cancel previous preview session when a newer edit supersedes it (debounce + generation id)
- `output.execute` / bake apply nodes must not run as auto-preview side effects
- World writes only via world-thread policy (existing bake/preview services)

## Cancellation policy (draft)

| Layer | Behavior |
|-------|----------|
| Session cancel | Stop scheduling new nodes; in-flight compute may finish or check token |
| Superseded preview | Cancel older session; do not apply stale preview results |
| World work in flight | Prefer abort via existing bake/preview cancel APIs; no orphan mutations |
| Scheduler shutdown | Mirror bake: request cancel first; optional flush only on client/server stop |

## Incremental + cache

- Keep `IncrementalExecutionOptions.previewDefaults()` (skip cached nodes in partial scope) as the preview default
- Session-local cache vs graph-level cache ownership must be explicit in the freeze pass
- Negative / icon caches stay in UI layer (Phase E) — out of Runtime 2.0 core

## Migration plan (implementation order after freeze)

1. Introduce scheduler + session **beside** current `NodeExecutor` (facade).
2. Route **auto-preview only** through the new path.
3. Move Apply / manual Run to sessions.
4. Remove pool ownership from legacy executor.
5. Add GameTests: superseded preview cancel, incremental dirty scope smoke.

## Non-goals for Runtime 2.0 v1

- Full editor interaction mode coordinator
- Build-time node catalog
- Graph format version migrations
- Rewriting ImGuiNodeEditor
- Distributed / multiplayer execution

## Open questions (resolve in freeze review)

1. Client vs server: one scheduler each, or server-only compute with client preview mirror?
2. Debounce window for auto-preview (ms) and max concurrent preview sessions (likely 1).
3. How exec-frontier graphs participate in incremental preview (dataflow-only first?).
4. Metrics surface: BakeStatus-like node vs editor HUD vs logs.

## Freeze checklist

- [x] Phase A tagged (`v0.7-stability`)
- [ ] Answers to open questions written here
- [ ] Public API sketch (package + key methods) agreed
- [ ] Vertical-slice acceptance tests listed
- [ ] Then start coding — still one slice at a time
