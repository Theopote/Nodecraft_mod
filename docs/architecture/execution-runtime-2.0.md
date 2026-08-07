# Execution Runtime 2.0 — Design Freeze

Parent roadmap: [`../development/advancement-0.7-to-0.8.md`](../development/advancement-0.7-to-0.8.md)

**Status:** Design freeze **RESOLVED** (2026-08-08). Vertical-slice coding may start after this document is treated as the contract.

## Problem

`NodeExecutor` currently concentrates too many responsibilities:

- Graph execution session
- Thread lifecycle / pool (`Executors.newSingleThreadExecutor` **per** `NodeExecutor` instance)
- Incremental scope
- Exec frontier
- World-thread routing
- Preview cleanup hooks
- Cache / error handling

Auto-preview already has dirty propagation + `IncrementalExecutionPlanner`, but each preview run constructs a new `NodeExecutor` (new worker thread) and **does not cancel** an in-flight run — it only skips starting while `isExecuting()`.

Evidence:

- `ImGuiNodeEditor.maybeAutoExecutePreviewGraph()` — debounce `250ms`, poll `750ms`, `new NodeExecutor(...)` + `executeAsync()`
- `NodeExecutor` field `executorService` shut down in `finally` after each run
- Cache lives on `NodeGraph.getExecutionCache()` (graph-scoped, not session-owned)

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
NodeExecutionScheduler   ◄── shared, long-lived on the CLIENT editor process
        │
        ├── Worker queue (compute nodes)
        ├── World-thread bridge (via ExecutionContext.callOnWorldThread)
        ├── Cancellation tokens + generation id
        ├── Priority (interactive preview > manual run)
        └── Session registry (at most one active PREVIEW session)
                │
                ▼
        ExecutionSession
                - one logical run / generation
                - does not own the pool
```

## Proposed types (boundaries)

| Type | Responsibility |
|------|----------------|
| `NodeExecutionScheduler` | Shared workers, submit/cancel session, metrics counters |
| `ExecutionSession` | One logical run: plan, cursor, cancel flag, generation id, result |
| `ExecutionPlan` | Immutable: mode, topo/frontier seeds, incremental scope, options |
| `CancellationToken` | Cooperative cancel checked between nodes |
| `WorldWork` | Supplier/Runnable that **must** use `ExecutionContext.callOnWorldThread` |

Package (proposed): `com.nodecraft.nodesystem.execution.runtime`

`NodeExecutor` becomes a thin facade over `ExecutionSession` during migration, then shrinks or disappears.

## Freeze resolutions (open questions)

### 1. Client vs server: where does the scheduler live?

**Decision: Client-owned scheduler for editor auto-preview / interactive runs.**

| Concern | Resolution |
|---------|------------|
| Who triggers preview today | Client only — `ImGuiNodeEditor` on the render/UI path |
| Where compute runs today | Dedicated `nodecraft-graph-worker-*` thread created by `NodeExecutor` |
| World access today | Prefer integrated-server overworld + `ServerPlayerEntity` when available; `callOnWorldThread` for `world.*` / `output.*` / `input.context.*` |
| Dedicated server | No ImGui editor → no auto-preview scheduler required in v1 |
| Bake / Apply | Remain server-tick services (`BakePlacementService`); not owned by the preview scheduler |

**Not** “server-only compute with client mirror” for v1 — that would relocate the editor loop without benefit. Dedicated-server headless execute can be a later session mode using the same `ExecutionSession` types if needed.

### 2. Debounce and concurrency

**Decision: keep current timing; tighten supersede semantics.**

| Parameter | Value | Notes |
|-----------|-------|-------|
| Dirty debounce | **250 ms** | Existing `AUTO_PREVIEW_DEBOUNCE_MS` |
| Idle poll | **750 ms** | Existing `AUTO_PREVIEW_POLL_INTERVAL_MS` (optional; may drop later) |
| Max concurrent **preview** sessions | **1** | Already effectively 1 |
| Supersede policy | **Cancel previous** when a newer dirty generation is ready | Today waits for finish — this is the main Runtime 2.0 behavior change |
| Generation id | Monotonic `dirtyVersion` / `graphDirtyEpoch` | Stale completions must not apply preview |

Manual Run may share the scheduler at lower priority but must not starve preview cancel.

### 3. Exec-frontier graphs in incremental preview

**Decision: v1 auto-preview is dataflow-first; exec graphs get a safe degraded mode.**

| Case | v1 behavior |
|------|-------------|
| No `exec` edges | Incremental dataflow scope via `IncrementalExecutionPlanner` + `previewDefaults()` |
| Has `exec` edges | **Full exec-flow run is allowed only if preview-safe** (see side-effect policy). Incremental exec frontier is **out of scope for v1**. |
| Partial scope + exec | If dirty scope is set on an exec graph, **fall back to full reachable exec run** or skip auto-preview with a status message — prefer **skip auto-preview for unsafe exec graphs** until Phase B.2 |

Do not invent incremental exec semantics in the first vertical slice.

### 4. Metrics surface

**Decision: reuse `ExecutionProfiler` + editor HUD; no new BakeStatus-like node in v1.**

| Surface | Role |
|---------|------|
| `ExecutionProfiler.Profile` | Already produced per run (`NodeExecutor.getLastExecutionProfile`) |
| Editor / menu status | `MenuBarRenderer` already formats profile summaries for manual execute |
| Logs | Debug lines for preview reason / scope / generation (keep) |
| New graph node | **Defer** — BakeStatus is for bake tasks; don’t conflate |

Scheduler should expose: `activeSessionId`, `generation`, `cancelled`, `lastProfile` for the editor HUD.

## Side-effect policy (must land with vertical slice)

Today `requiresWorldThread` routes `output.execute.*` to the server thread but **does not skip** those nodes during auto-preview. That means Apply/Undo/Bake-class nodes can still run if present in scope.

**Runtime 2.0 preview mode MUST skip permanent side effects:**

- Skip (or no-op) typeId prefixes: `output.execute.`
- Allow preview writers: `output.preview.*` and geometry viewers that use `TrackedPreviewPlacementService` / `PreviewManager`
- World **reads** (`world.read.*`, `input.context.*`) remain allowed via world-thread
- World **writes** outside preview services are forbidden in `ExecutionPlan.Mode.PREVIEW`

Enforce in `ExecutionSession` / plan options, not only by convention.

## Cache ownership

**Decision: keep `NodeExecutionCache` on `NodeGraph` (graph-level).**

- Preview and manual runs share cache validity
- Session does not deep-copy cache
- Structure dirty (`markGraphStructureDirty`) clears cache as today
- Cancelled / failed preview must not poison cache with partial writes — only `executionCache.record(node)` after successful `compute` (already true)

## Auto-preview path (must-win vertical slice)

```text
property / connection change
  → mark dirty + invalidate scope
  → debounce 250ms
  → cancel previous PREVIEW session (generation N-1)
  → IncrementalExecutionPlanner.scope(dirty)
  → ExecutionPlan.preview(scope, previewDefaults, skip output.execute)
  → client NodeExecutionScheduler.submit(session N)
  → recompute affected subgraph only
  → preview services update on world thread
  → ignore results if generation != latest
```

## Cancellation policy

| Layer | Behavior |
|-------|----------|
| Session cancel | Stop scheduling new nodes; check token between nodes; interrupt worker if cooperative checks miss |
| Superseded preview | Cancel older session; **do not apply** stale preview / cache records from cancelled run |
| World work in flight | Preview tracked placements: prefer clear/replace via existing preview APIs; bake Apply must not be in preview mode |
| Scheduler shutdown | Request cancel all sessions; no sync flush of giant bake (bake uses `shutdownFlush` separately) |

## Public API sketch

```text
package com.nodecraft.nodesystem.execution.runtime;

enum ExecutionMode { PREVIEW, MANUAL, HEADLESS }

record ExecutionPlan(
    ExecutionMode mode,
    Set<UUID> scopeNodeIds,          // null = full
    IncrementalExecutionOptions options,
    boolean skipOutputExecuteSideEffects
)

interface CancellationToken {
    boolean isCancelled();
    void cancel();
}

interface ExecutionSession {
    UUID sessionId();
    long generation();
    CancellationToken cancellation();
    CompletableFuture<Boolean> result();
    ExecutionProfiler.Profile lastProfile(); // after complete
}

interface NodeExecutionScheduler {
    static NodeExecutionScheduler client(); // singleton for editor
    ExecutionSession submit(NodeGraph graph, ExecutionContext ctx, ExecutionPlan plan, long generation);
    void cancelPreview(); // cancels active PREVIEW session
    Optional<ExecutionSession> activePreview();
}
```

Migration facade:

```text
NodeExecutor.executeAsync()
  → scheduler.submit(..., MANUAL, ...)   // interim
ImGuiNodeEditor.maybeAutoExecutePreviewGraph()
  → scheduler.cancelPreview(); submit(..., PREVIEW, dirtyGeneration)
```

## Vertical-slice acceptance tests

1. **Unit:** submitting preview while another preview runs cancels the older session; only latest generation’s completion is accepted.
2. **Unit:** `ExecutionPlan` preview mode never invokes `output.execute.*` `compute` (fake/spy node or type-id gate test).
3. **Unit/GameTest:** incremental dirty scope recomputes downstream only; upstream cached nodes skipped under `previewDefaults()`.
4. **GameTest (integrated):** property change updates tracked preview blocks without creating a new bake history entry.
5. **Manual smoke:** rapid slider edits do not accumulate `nodecraft-graph-worker-*` threads (shared scheduler).

## Migration plan (after freeze)

1. Add `runtime` types + client singleton scheduler **beside** `NodeExecutor`.
2. Route **auto-preview only** through scheduler + cancel/supersede + skip `output.execute`.
3. Move manual Run to sessions.
4. Remove per-executor pool.
5. Land acceptance tests above.

## Non-goals for Runtime 2.0 v1

- Full editor interaction mode coordinator
- Build-time node catalog
- Graph format version migrations
- Rewriting ImGuiNodeEditor (beyond extracting AutoPreviewController later)
- Distributed / multiplayer execution
- Incremental exec-frontier scheduling

## Freeze checklist

- [x] Phase A tagged (`v0.7-stability`)
- [x] Answers to open questions written here
- [x] Public API sketch (package + key methods) agreed
- [x] Vertical-slice acceptance tests listed
- [ ] Coding starts — **first PR = scheduler + preview path only**
