# NodeCraft Advancement: 0.7 Stability → 0.8 Interactive Runtime

> Source of truth for the next project phase.  
> Older `FINAL-*` / `FIXES-*` / `*-COMPLETE.md` notes are historical; do not treat them as current policy.

## Current position

Rough version ladder:

| Stage | Theme | Status |
|-------|--------|--------|
| 0.5 | Preview / Bake | Done (feature) |
| 0.6 | Execution (dataflow + exec frontier) | Done (baseline) |
| **0.7** | **Stability** (transaction / cancel / CI) | **Near complete — closeout below** |
| **0.8** | **Interactive Runtime** (scheduler / incremental auto-preview) | **Next major phase** |
| 0.9 | Compatibility / format freeze | Later |
| 1.0 | Release | Later |

One-line goal for 0.8:

> Move from “run a node graph once” to “continuously maintain a live, incremental, cancellable, observable computation graph.”

## Working rule

1. **Write / update the advancement doc for the phase** (this file + linked design notes).
2. **Define exit gates** (tests, CI, tag, or design freeze).
3. **Implement only the current gate’s checklist** — no parallel eight-track rewrites.
4. **Mark the gate PASS**, then open the next phase.

Do not start Phase B implementation until Phase A is PASS and tagged.

---

## Phase A — Transaction Final Pass (now)

**Goal:** Formally close Bake / Preview / ApplyChanges P0–P1 stability.

### Checklist

| Work | Priority | Status |
|------|----------|--------|
| Duplicate `BlockPos` rollback (`putIfAbsent` originals) | P0 | Done |
| Duplicate-position GameTests | P0 | Done |
| Rollback failure counts + `ROLLBACK_FAILED` + BakeStatusNode | P1 | Done |
| `cancelAll` does not sync-drain giant rollback; `shutdownFlush` for stop | P1 | Done |
| Timeout → rollback GameTest | P0 | Done (`bakeApplyTimeoutRollsBackWorld`) |
| Latest CI green (incl. new GameTests) | Gate | **In progress** — pushed `360b74f`, awaiting Actions |
| Tag `v0.7-stability` (or `stability-baseline`) | Gate | **TODO after CI green** |

Detail checklist: [`phase-a-stability-closeout.md`](./phase-a-stability-closeout.md)

### Phase A exit criteria

- [ ] Timeout abort rolls world back; history unchanged; terminal state is `TIMED_OUT` (or `ROLLBACK_FAILED` if restores fail)
- [ ] CI build + unit tests + GameTests green on the closeout commit
- [ ] Git tag `v0.7-stability` on that commit
- [ ] Announce: **NodeCraft P0/P1 Stability Audit: PASS**

Only then open Phase B coding.

---

## Phase B — Execution Runtime 2.0 (next major)

**Design note (write before coding):** [`../architecture/execution-runtime-2.0.md`](../architecture/execution-runtime-2.0.md)

### Intent

| Today (`NodeExecutor`) | Target |
|------------------------|--------|
| Owns graph run + thread pool + frontier + routing + preview hooks | One **execution session** |
| New worker lifecycle on many preview paths | Shared **NodeExecutionScheduler** |

Target shape:

```text
Graph
  → ExecutionPlan / IncrementalExecutionPlanner
  → NodeExecutionScheduler
       ├── worker execution
       ├── world-thread work
       ├── cancellation
       ├── incremental re-exec
       ├── priority
       └── task / session lifecycle
```

### Why this is next

Dirty propagation, incremental planner, and preview already exist. The missing product leap is:

```text
property change → dirty nodes → IncrementalExecutionPlanner
  → NodeExecutionScheduler → affected subgraph only → preview update
```

Not: spin a full `NodeExecutor` worker lifecycle on every edit.

### Phase B exit criteria (draft)

- [ ] Design doc reviewed (API boundaries, cancel, world-thread, session vs scheduler)
- [ ] Scheduler owns pools / queue; `NodeExecutor` is session-scoped
- [ ] Auto-preview path uses incremental plan + shared scheduler
- [ ] Cancellation is first-class (session + in-flight world work policy)
- [ ] GameTests / contract tests cover cancel + incremental preview smoke

---

## Later phases (do not start in parallel)

Ordered backlog — pick one after B has a working vertical slice:

| Phase | Theme | Notes |
|-------|--------|------|
| B′ | Node Contract Test Suite | Can land a thin suite right after A tag; cheap regression fence |
| C | Build-time `NodeCatalog` | After B vertical slice; unify registry / AI schema / docs |
| D | Full contract suite expansion | ID uniqueness, ports, ser/de, no preview side effects on `output.execute` |
| E | Node Library display / icon caches | After interactive path is stable |
| F | `ImGuiNodeEditor` split (`EditorDocumentState` first) | After auto-preview controller boundary is clear |
| G | `EditorInteractionMode` / input capture coordinator | After F first cut |
| H | Docs `architecture/` + `contracts/` + move old FINAL/FIXES → `history/` | Continuous, opportunistic |
| I | `GraphFormatVersion` + migration era | Before public asset compatibility promises |

## Explicit non-goals (now)

- Adding 50–100 nodes in bulk
- Full `ImGuiNodeEditor` rewrite
- Mass SVG→PNG conversion
- New UI framework
- Redesigning the graph data structure
- Multiplayer collab
- Large “AI auto-model” features

These move the kernel again after it just became auditable.

---

## Suggested week-one sequence

1. Finish Phase A TODO (timeout GameTest → CI → tag).
2. Freeze Phase B design in `execution-runtime-2.0.md` (APIs + non-goals).
3. Implement the smallest B vertical slice: **incremental auto-preview session on shared scheduler**.
4. Optionally add a thin Node Contract suite (ID uniqueness) as a side fence — not a second mainline.
