# NodeCraft Advancement: 0.7 Stability → 0.8 Interactive Runtime

> Source of truth for the 0.8 phase track.  
> Older `FINAL-*` / `FIXES-*` / `*-COMPLETE.md` notes are historical; see [`../history/`](../history/). Do not treat them as current policy.

## Current position

| Stage | Theme | Status |
|-------|--------|--------|
| 0.5 | Preview / Bake | Done |
| 0.6 | Execution (dataflow + exec frontier) | Done |
| **0.7** | **Stability** | **PASS (`v0.7-stability`)** |
| **0.8** | **Interactive Runtime** | **PASS (phases A–I)** |
| 0.9 | Compatibility / format freeze follow-through | Later |
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

---

## Phase B′ — Node Contract Test Suite — PASS

Contracts: [`../contracts/node-metadata.md`](../contracts/node-metadata.md)

---

## Phase C — Build-time NodeCatalog — PASS

Design: [`../architecture/node-catalog.md`](../architecture/node-catalog.md)

---

## Phase D — Expand contracts — PASS

Contracts: [`../contracts/preview-side-effects.md`](../contracts/preview-side-effects.md), [`../contracts/node-state-serde.md`](../contracts/node-state-serde.md)

---

## Phase E — Node Library display / icon caches — PASS

Design: [`../architecture/node-library-display-cache.md`](../architecture/node-library-display-cache.md)

---

## Phase F — EditorDocumentState split — PASS

Design: [`../architecture/editor-document-state.md`](../architecture/editor-document-state.md)

---

## Phase G — EditorInteractionMode — PASS

Design: [`../architecture/editor-interaction-mode.md`](../architecture/editor-interaction-mode.md)

---

## Phase H — Docs history cleanup — PASS

Detail: [`docs-history-cleanup.md`](./docs-history-cleanup.md)

---

## Phase I — GraphFormatVersion — PASS

Design: [`../architecture/graph-format-version.md`](../architecture/graph-format-version.md)  
Contract: [`../contracts/graph-format.md`](../contracts/graph-format.md)

| Work | Status |
|------|--------|
| `GraphFormatVersion` canonical API + policy helpers | PASS |
| `GraphFormat` deprecated alias | PASS |
| Migration / serializer wired to `GraphFormatVersion` | PASS |
| `GraphFormatVersionContractTest` | PASS |

---

## 0.8 result

**Interactive Runtime track (A–I): PASS**

Suggested optional tag: `v0.8-interactive-runtime` after CI green on this tip.

## Explicit non-goals (still)

- Bulk node expansion, full editor rewrite into a new UI framework, graph schema redesign beyond versioned migrations, multiplayer, large AI auto-model features.
- Staged `ImGuiNodeEditor` ownership breakup **is** an in-goal 0.9 track (see [`../architecture/imgui-node-editor-breakup.md`](../architecture/imgui-node-editor-breakup.md)); not a single-shot rewrite.
