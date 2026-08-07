# Phase A — Stability Closeout Checklist

Parent: [`advancement-0.7-to-0.8.md`](./advancement-0.7-to-0.8.md)

## Already landed (do not re-open unless regression)

- **Duplicate position transaction:** `BakeTask` records originals with `putIfAbsent`; rollback restores pre-txn state.
- **GameTests:** `bakeDuplicatePosCancelRestoresPreTransactionState`, `bakeDuplicatePosCommitUndoRestoresOriginal`.
- **Rollback failure reporting:** `attempted` / `restored` / `failed` → terminal `ROLLBACK_FAILED`; exposed on `TaskSnapshot` + `BakeStatusNode`.
- **cancelAll:** request-cancel only; time-sliced rollback continues via ticks.
- **shutdownFlush:** sync drain for `SERVER_STOPPING` / test reset only.
- **CI #29** on preset AccessDenied fix was green; re-verify after this closeout commit.

## Remaining work (in order)

### 1. Timeout rollback GameTest (P0) — Done

Implemented: `bakeApplyTimeoutRollsBackWorld` in `NodeCraftGameTest`.

### 2–4. Local verify / CI / Tag — Done

- Pushed closeout commits through `9dbfc5e`
- CI workflow badge: **passing** (runs #30 / #31 completed)
- Annotated tag **`v0.7-stability`** pushed to origin

## Phase A result

**NodeCraft P0/P1 Stability Audit: PASS**

Next: design-freeze review of `docs/architecture/execution-runtime-2.0.md`, then Runtime 2.0 vertical slice (not ad-hoc coding).

## Out of scope for Phase A

- NodeExecutionScheduler implementation
- EditorDocumentState extraction
- Build-time NodeCatalog
- Docs history migration of all FINAL/FIXES files (optional drive-by only)

## Done definition

All checkboxes in the Phase A section of `advancement-0.7-to-0.8.md` are checked, tag exists on green CI commit. **Met.**

