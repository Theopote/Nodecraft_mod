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

Covers:

- Mid-apply `cancelTask(..., TIMED_OUT)` after partial progress
- World restored to pre-transaction state
- History not committed
- Terminal snapshot `TIMED_OUT` with `rollbackFailedCount == 0`

### 2. Local verify

```bash
./gradlew --no-daemon test
./gradlew --no-daemon build   # includes GameTests in CI path
```

### 3. CI gate

- Push closeout commit
- Wait for Actions **success** (not only `in_progress`)
- Confirm GameTest count includes new timeout case

### 4. Tag

```bash
git tag -a v0.7-stability -m "P0/P1 Stability Audit PASS: bake/preview transaction baseline"
git push origin v0.7-stability
```

Alternate name: `stability-baseline` (prefer one canonical tag: **`v0.7-stability`**).

### 5. Announce

Record in PR / release note one line:

> NodeCraft P0/P1 Stability Audit: PASS (transaction rollback, cancel/timeout abort, CI green). Next: Runtime 2.0 design freeze.

## Out of scope for Phase A

- NodeExecutionScheduler implementation
- EditorDocumentState extraction
- Build-time NodeCatalog
- Docs history migration of all FINAL/FIXES files (optional drive-by only)

## Done definition

All checkboxes in the Phase A section of `advancement-0.7-to-0.8.md` are checked, tag exists on green CI commit.
