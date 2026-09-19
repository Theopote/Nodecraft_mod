# AI assistant subsystem

Design freeze for treating AI as a **first-class subsystem**, not a panel feature.

## Status

AI has already grown into a full pipeline:

| Concern | Current home (examples) |
|---------|-------------------------|
| Intent | `AiIntentAnalysisService` |
| Schema | `AiNodeSchemaCatalog` / exporter (+ build stub U2) |
| Planner (mock) | `AiMockPlanService` (~60 KB) |
| Planner (remote) | `AiRemotePlannerService` (~55 KB) |
| Orchestration (partial) | `AiRemotePlanningOrchestrator`, `AiPlanApplyCoordinatorService` |
| DSL | `AiGraphDslSupport`, `AiGraphPlanDslAdapterService`, `AiPlanDslWorkflowService` |
| Diff / validation-ish | `AiGraphDiffService`, dry-run report services |
| Apply | `AiGraphApplyService` / adapters |
| History | `AiConversationHistoryService`, session stores |
| Settings | `AiSettingsStore` |
| UI | `AiAssistantPanel` (view) + `AiAssistantController` / `AiAssistantUiBindings` + renderers / component |

**Progress:** A1/A2 landed — panel is view-only; controller wires settings + UI events;
`AiPlanningSession` owns in-flight prompt/plan/apply bookkeeping. Remaining risk is still
a large controller method surface until A3/A4 split planner/validator façades.

## Target layering (frozen)

```
AI UI (panel / renderers)
    → AiAssistantController
        → AiPlanningSession
            → AiPlannerService          (mock | remote provider)
            → AiPlanValidator
            → AiGraphApplyService
```

| Layer | Owns | Must not own |
|-------|------|--------------|
| **AI UI** | ImGui widgets, layout, user gestures | HTTP, graph mutate, plan parsing |
| **AiAssistantController** | Wire UI events → session; surface status | Provider wire format; apply algorithms |
| **AiPlanningSession** | One planning run: prompt, schema slice, pending plan, apply/undo bookkeeping | Widget state; raw sockets |
| **AiPlannerService** | Produce plan text / structured payload | Graph mutation |
| **AiPlanValidator** | Schema / DSL / connection / type gates before apply | Side effects on canvas |
| **AiGraphApplyService** | Patch / replace / rollback against `GraphApplyTarget` | Prompt construction |

Existing services should **slide into** these layers; do not invent a second parallel stack.

## Resolutions

| Topic | Decision |
|-------|----------|
| Subsystem status | AI is a first-class module; treat refactors like editor breakup |
| Panel role | UI + thin callbacks only; no new orchestration in `AiAssistantPanel` |
| Controller | Extract `AiAssistantController` as the first structural cut |
| Session | Explicit `AiPlanningSession` for in-flight remote/mock plan state (replace ad-hoc panel fields) |
| Planner façade | Unify mock/remote behind `AiPlannerService` (or keep adapters behind one interface) |
| Validator | Named `AiPlanValidator` boundary even if first impl delegates to existing DSL/diff/dry-run helpers |
| Apply | Keep `AiGraphApplyService` as the mutate/rollback owner |
| Registration / catalog | Unrelated — keep using shared node-catalog SoT for schema stubs |

## API key policy

### Current (accepted for development builds)

- Default: **do not** persist the key (`Remember API key on disk` off)
- Opt-in remember: plain text under `nodecraft/config/ai_settings.json`
- Documented honestly in README / settings UI

### 1.0 target (pick one primary path)

1. **OS credential store** (preferred for desktop clients), or
2. **Local proxy** — client talks to a user-run proxy; **discourage** long-lived provider keys in the game process

Until 1.0 lands: no new features that *require* disk-persisted provider keys; keep the remember toggle opt-in and loud.

## Non-goals (this freeze)

- Rewriting mock plan templates wholesale
- Changing prompt product copy
- Shipping OS credential / proxy in the same slice as the controller extraction
- Merging AI into `nodesystem` packages

## Suggested slices

| Slice | Deliverable | Status |
|-------|-------------|--------|
| **A0** | This architecture freeze | **done** |
| **A1** | Extract `AiAssistantController` — panel becomes view | **done** |
| **A2** | Introduce `AiPlanningSession` for remote/mock in-flight state | **done** |
| **A3** | `AiPlannerService` façade over mock + remote | next |
| **A4** | `AiPlanValidator` boundary + wire before apply | pending |
| **A5** | 1.0 credential store **or** local-proxy guidance (product choice) | pending |

### A1 notes

- `AiAssistantController` owns settings persistence, remote/mock submit, poll, apply/undo/dry-run, session flush
- `AiAssistantUiBindings` holds ImGui widgets + topology preview UI state (same pattern as `PropertyEditSession`)
- `AiAssistantPanel` (~300 lines) is view-only: render + gesture → controller
- Tests: `AiAssistantControllerTest` (no ImGui render)

### A2 notes

- `AiPlanningSession` owns in-flight planning state: last prompt, plan status, pending plan (via component), DSL repair / graph-expansion counters, world-context snapshot, apply/undo bookkeeping
- `AiAssistantController` keeps settings I/O, connection-test future, provider autofill label, and wires UI → session
- Tests: `AiPlanningSessionTest`

Exit for A1: `AiAssistantPanel` no longer calls remote planner / apply / settings persistence directly;
tests cover controller without ImGui (`AiAssistantControllerTest`).

## Related

- `docs/architecture/imgui-node-editor-breakup.md` (same “UI must not own the system” pattern)
- `docs/architecture/property-panel-breakup.md`
- `docs/architecture/node-catalog.md` (AI schema stub U2)
- `README.md` (current API key disclosure)
