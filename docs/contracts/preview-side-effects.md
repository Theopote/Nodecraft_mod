# Preview side-effect policy

Formal contract for Execution Runtime 2.0 preview mode. Source of truth for what
auto-preview may and may not run.

## Rule

| Mode | `ExecutionPlan.skipOutputExecuteSideEffects` | Permanent side-effect nodes |
|------|-----------------------------------------------|-----------------------------|
| `PREVIEW` (`ExecutionPlan.preview`) | `true` | **Skipped** — `compute` / `processNode` must not run |
| `MANUAL` (`ExecutionPlan.manual`) | `false` | Run normally |

## Permanent side-effect definition

A node is a permanent side-effect node iff its `typeId` starts with:

```text
output.execute.
```

Helper: `com.nodecraft.nodesystem.execution.runtime.PreviewSideEffectPolicy`

This includes bake / apply / undo / redo / clear-all-previews-style execute nodes.
It does **not** include `output.preview.*` (tracked preview writers are allowed).

## Implementation points

| Layer | Behavior |
|-------|----------|
| `ExecutionPlan.preview(...)` | Sets `skipOutputExecuteSideEffects = true` |
| `NodeExecutor.shouldExecuteNode` | Returns false when skip flag + permanent side-effect |
| `AutoPreviewController` / GameTest smoke | Must observe zero `output.execute.*` executions |

## Catalog contract

Every registered node whose `typeId` starts with `output.execute.` must be classified
by `PreviewSideEffectPolicy.isPermanentSideEffectNode`. Suite:
`PreviewSideEffectContractTest`.

## Non-goals

- Incremental exec-frontier preview
- Skipping `world.*` writes outside `output.execute.*` (separate hardening later)
- Changing bake/apply semantics in manual runs
