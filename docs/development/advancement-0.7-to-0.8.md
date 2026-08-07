# NodeCraft Advancement: 0.7 Stability → 0.8 Interactive Runtime

> Source of truth for the next project phase.  
> Older `FINAL-*` / `FIXES-*` / `*-COMPLETE.md` notes are historical; do not treat them as current policy.

## Current position

| Stage | Theme | Status |
|-------|--------|--------|
| 0.5 | Preview / Bake | Done |
| 0.6 | Execution (dataflow + exec frontier) | Done |
| **0.7** | **Stability** | **PASS (`v0.7-stability`)** |
| **0.8** | **Interactive Runtime** | **B–F PASS — next: G EditorInteractionMode** |
| 0.9 | Compatibility / format freeze | Later |
| 1.0 | Release | Later |

One-line goal for 0.8:

> Move from “run a node graph once” to “continuously maintain a live, incremental, cancellable, observable computation graph.”

## Working rule

1. Write / update the advancement doc for the phase.
2. Define exit gates.
3. Implement only the current gate’s checklist.
4. Mark the gate PASS, then open the next phase.

---

## Phase A — Transaction Final Pass — PASS

Tag: `v0.7-stability`. Detail: [`phase-a-stability-closeout.md`](./phase-a-stability-closeout.md)

---

## Phase B — Execution Runtime 2.0 — Vertical slice PASS

Design: [`../architecture/execution-runtime-2.0.md`](../architecture/execution-runtime-2.0.md)

### Landed

- [x] Design freeze resolutions
- [x] `ClientNodeExecutionScheduler` shared worker
- [x] Auto-preview supersede cancel + skip `output.execute.*`
- [x] Manual Run on shared scheduler
- [x] `executeSync` creates no ephemeral pool (nested subgraph-safe)
- [x] `AutoPreviewController` extracted
- [x] Unit + GameTest smoke for preview contracts

### Optional follow-ups (not blocking B′)

- Editor HUD for active session / generation
- Incremental dirty-scope GameTest beyond smoke
- Tag `v0.8-runtime-slice` after CI green on this fence

---

## Phase B′ — Node Contract Test Suite — PASS

**Goal:** Cheap regression fence for the ~500+ node catalog.

Contracts: [`../contracts/node-metadata.md`](../contracts/node-metadata.md)

| Work | Status |
|------|--------|
| Unique registry / annotation IDs | PASS |
| Annotation id == runtime `typeId` | PASS |
| Port IDs unique per node | PASS |
| Non-blank category | PASS |
| Scanner skips nested/anonymous helpers | PASS |

Exit: `NodeContractTest` green on CI.

---

## Phase C — Build-time NodeCatalog — PASS

Design: [`../architecture/node-catalog.md`](../architecture/node-catalog.md)

| Work | Status |
|------|--------|
| Design freeze | PASS |
| Gradle `generateNodeCatalog` → `GeneratedNodeCatalog` | PASS |
| `DefaultNodeProvider` primary path = catalog | PASS |
| `AutoNodeScanner` fallback when catalog empty | PASS |
| `NodeCatalogContractTest` | PASS |

Exit: catalog registration used in prod path; contract tests green.

---

## Phase D — Expand contracts — PASS

| Work | Status |
|------|--------|
| Preview side-effect policy formalized (`PreviewSideEffectPolicy`) | PASS |
| `PreviewSideEffectContractTest` (catalog `output.execute.*`) | PASS |
| Node state ser/de catalog roundtrip | PASS |
| Docs: `preview-side-effects.md`, `node-state-serde.md` | PASS |

Contracts:

- [`../contracts/preview-side-effects.md`](../contracts/preview-side-effects.md)
- [`../contracts/node-state-serde.md`](../contracts/node-state-serde.md)

---

## Phase E — Node Library display / icon caches — PASS

Design: [`../architecture/node-library-display-cache.md`](../architecture/node-library-display-cache.md)

| Work | Status |
|------|--------|
| `NodeIconPathResolver` (pure path logic) | PASS |
| Icon resolution memo in `NodeIconManager` | PASS |
| `NodeLibraryDisplayCache` for sorted lists | PASS |
| Unit tests for resolver + display cache | PASS |

---

## Phase F — EditorDocumentState split — PASS

Design: [`../architecture/editor-document-state.md`](../architecture/editor-document-state.md)

| Work | Status |
|------|--------|
| `EditorDocumentState` (graph / positions / dirty) | PASS |
| `ImGuiNodeEditor` + `ImGuiNodeIO` wired to document | PASS |
| Auto-preview uses document as `DirtyVersionSource` | PASS |
| `EditorDocumentStateTest` | PASS |

---

## Later phases (ordered)

| Phase | Theme |
|-------|--------|
| G | `EditorInteractionMode` |
| H | Docs history cleanup |
| I | `GraphFormatVersion` |

## Explicit non-goals (now)

- Bulk node expansion, full editor rewrite, new UI framework, graph schema redesign, multiplayer, large AI auto-model features.
