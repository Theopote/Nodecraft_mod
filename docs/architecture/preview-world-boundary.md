# Preview world-state boundary

Target ownership for preview vs permanent world mutation.

## Current shape (healthy)

```text
output.preview.*
        ↓
PreviewManager / PreviewRenderer
        ↓
GHOST  →  pure visual overlay (default)

TrackedPreviewPlacementService
        ↓
TRACKED_WORLD  →  temporary world mutation + restore  (compat / special)

output.execute.*
        ↓
BakePlacementService
        ↓
permanent world write + undo/redo
```

This is already much clearer than the older GHOST-invisible / ORIGINAL / TRACKED_WORLD
confusion. The residual problem is **two preview semantics**, not missing bake.

## Problem

While `TrackedPreviewPlacementService` exists as a first-class preview path,
Preview means both:

1. **Render-only** — no world mutation (GHOST)
2. **Mutate-then-restore** — temporary blocks with cleanup / affinity / unload /
   cancel / supersede / crash-recovery complexity (TRACKED_WORLD)

That second path keeps expanding operational surface area even when most users
only need visual feedback.

## Target boundary

| Concern | Owner | Backend |
|---------|-------|---------|
| Default geometry / block preview | `PreviewManager` → `PreviewRenderer` | **GHOST** |
| Compatibility / special tooling preview | `TrackedPreviewPlacementService` | **TRACKED_WORLD** (demoted) |
| Permanent placement | `BakePlacementService` via `output.execute.*` | bake / apply only |

**Rules of thumb:**

1. New preview features default to **GHOST**.
2. Do **not** delete `TRACKED_WORLD` until GHOST covers the tooling cases that
   still need real block interaction (collision probes, screenshot parity, etc.).
3. Any durable world change goes through **BakePlacementService**, never through
   tracked preview “promotion”.
4. Auto-preview (`ExecutionPlan.PREVIEW`) may run `PREVIEW_WRITE` nodes, but
   product guidance should steer authors toward GHOST; tracked preview remains
   opt-in on the node.

## Demotion plan (incremental)

### Phase P0 — Policy + naming (this doc)

- Document the boundary (here).
- Mark `PreviewBackend.TRACKED_WORLD` as compatibility / special in API docs and
  property labels.
- Keep runtime behavior unchanged.

### Phase P1 — Product defaults (done)

- Inventory: no bundled presets / resources pin `TRACKED_WORLD`.
- `GeometryViewerNode.previewBackend` lives under **Advanced** (collapsed by default,
  sorted last) with a compat-oriented description.
- Property panel collapses `Advanced` / `Compatibility` sections by default.
- Runtime still accepts saved `TRACKED_WORLD` state (no forced migration).

### Phase P2 — Capability inventory (done)

See [Capability inventory](#capability-inventory-p2) below and
`TrackedWorldCapabilityInventory` (code + `TrackedWorldCapabilityInventoryTest`).

### Phase P3 — Freeze + compat gate (done); deletion deferred

**Landed now (freeze):**
- `TrackedWorldCapabilityInventory.FEATURE_FROZEN = true`
- `TrackedWorldCompatGate` — default OFF; opt-in via
  `-Dnodecraft.preview.trackedWorldCompat=true`
- New UI selections coerce TRACKED_WORLD → GHOST when gate is closed
- Property combo only lists GHOST for Ghost nodes; legacy TRACKED_WORLD nodes
  can still migrate off
- Saved-graph restore still keeps TRACKED_WORLD (`sanitizeRestored`)

**Still deferred (true retirement):** remove enum / service only after
`GHOST_GAPS` are closed (or accepted as Bake-only). Do not delete yet.

## Capability inventory (P2)

### Ghost cannot yet replace TRACKED_WORLD for

| Gap | Why it matters |
|-----|----------------|
| Collision / pathfinding / entity interaction | Real `BlockState` in the world |
| World queries reading preview cells | `world.getBlockState` / scanners |
| Lighting / occlusion / screenshot parity | Ghost is client overlay only |
| Vanilla / third-party systems that only see world blocks | No Ghost hook |

Permanent placement is **not** a Ghost gap — that is Bake.

### Call-site verdicts

| Owner | Role | Verdict |
|-------|------|---------|
| `GeometryViewerNode.previewBackend` | Product entry (Advanced opt-in) | **KEEP_FOR_GAP** — sole catalog chooser; default GHOST |
| `PreviewManager.showPreview(BLOCKS, TRACKED_WORLD)` | Dispatch | **KEEP_FOR_GAP** |
| `TrackedPreviewPlacementService` | Implementation | **KEEP_FOR_GAP** |
| `PreviewManager` backend-switch / hide | Lifecycle cleanup | **KEEP_LIFECYCLE** |
| `NodecraftLifecycleManager` | Lifecycle cleanup | **KEEP_LIFECYCLE** |
| `PreviewBlocksNode` | Product entry | **ALREADY_GHOST** |
| `PreviewManager` curve/points/vectors/… helpers | Product entry | **ALREADY_GHOST** |
| GameTest / unit tests | Test | allowed |

**No other catalog node** selects `TRACKED_WORLD`. Bundled presets do not pin it (P1).

### Policy for new work

1. New preview features → **GHOST** only.
2. New `TRACKED_WORLD` product usage requires a new inventory row + Ghost-gap justification.
3. Lifecycle clears may call `TrackedPreviewPlacementService` without being a “feature”.

## Non-goals

- Merging bake undo with tracked-preview restore stacks
- Deleting `TrackedPreviewPlacementService` in the same change as this policy
- Making GHOST mutate the world

## Related

- `docs/contracts/preview-side-effects.md` — which nodes may run in PREVIEW mode
- `docs/architecture/execution-runtime-2.0.md` — scheduler vs bake ownership
- `TrackedWorldCapabilityInventory`, `PreviewBackend`, `PreviewManager`,
  `TrackedPreviewPlacementService`, `BakePlacementService`
