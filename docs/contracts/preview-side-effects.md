# Preview side-effect policy

Formal contract for Execution Runtime 2.0 preview mode. Source of truth for what
auto-preview may and may not run.

## Rule

| Mode | `ExecutionPlan.skipOutputExecuteSideEffects` | Preview-forbidden nodes |
|------|-----------------------------------------------|-------------------------|
| `PREVIEW` (`ExecutionPlan.preview`) | `true` | **Skipped** — `compute` / `processNode` must not run |
| `MANUAL` (`ExecutionPlan.manual`) | `false` | Run normally |

## NodeEffect model

Every catalog node declares an execution capability via `@NodeInfo(effect = …)`:

| `NodeEffect` | Preview allowed | Examples |
|--------------|-----------------|----------|
| `PURE` | Yes | math, geometry, pattern nodes |
| `WORLD_READ` | Yes | `world.read.*`, `world.query.*`, `input.context.*` |
| `PREVIEW_WRITE` | Yes | `output.preview.*`, tracked preview services |
| `WORLD_WRITE` | **No** | `world.write.*`, bake/apply execute nodes |
| `FILE_IO` | **No** | `utilities.fileio.*`, `output.export.*` |
| `NETWORK` | **No** | (reserved) |
| `UI_EFFECT` | **No** | `output.debug.*`, bake status UI |
| `CONTEXT_READ` | Yes | `utilities.organization.graph_input` |
| `CONTEXT_WRITE` | Yes | `utilities.organization.graph_output` |
| `COMPOSITE` | Yes | `utilities.organization.subgraph` (child policy via nested executor) |
| `EDITOR_ONLY` | Yes | reserved editor metadata helpers |

Nested subgraph execution inherits the parent `skipOutputExecuteSideEffects` flag via
`NodeExecutor.nestedSync` so preview mode skips `WORLD_WRITE` / `FILE_IO` inside child graphs.

Helpers:

- `com.nodecraft.nodesystem.api.NodeEffect`
- `com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver`
- `com.nodecraft.nodesystem.execution.runtime.PreviewSideEffectPolicy`

Explicit overrides for type-id prefix mismatches live in `NodeEffectResolver`
(e.g. `output.execute.merge_block_placements` → `PURE`).

## Implementation points

| Layer | Behavior |
|-------|----------|
| `ExecutionPlan.preview(...)` | Sets `skipOutputExecuteSideEffects = true` |
| `NodeExecutor.shouldExecuteNode` | Returns false when preview mode + `!effect.isAllowedInPreview()` |
| `AutoPreviewController` / GameTest smoke | Must observe zero preview-forbidden node executions |

## Catalog contract

Every registered node must:

1. Declare `@NodeInfo(effect = …)` (not `UNSPECIFIED`)
2. Match `NodeEffectResolver.resolve(node)` at runtime

Suite: `PreviewSideEffectContractTest`.

## Non-goals

- Incremental exec-frontier preview
- Changing bake/apply semantics in manual runs
- Retiring `TRACKED_WORLD` in this contract (see
  `docs/architecture/preview-world-boundary.md` — demote first, delete later)

## Preview backend note

`PREVIEW_WRITE` allows `output.preview.*`. Product default remains **GHOST**
(render-only). `TRACKED_WORLD` is a compatibility / special backend that still
mutates then restores; permanent placement stays on `BakePlacementService`.
