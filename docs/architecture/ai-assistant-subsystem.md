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

**Progress:** A1–A5 landed — panel is view-only; controller wires settings + UI events;
`AiPlanningSession` owns in-flight state; `AiPlannerService` produces plans;
`AiPlanValidator` gates DSL parse and apply/dry-run;
`AiCredentialPolicy` freezes the 1.0 credential path (local proxy primary).

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
| Credentials (1.0) | **Local proxy primary**; env secondary; session/disk remember = dev only; OS store deferred |
| Registration / catalog | Unrelated — keep using shared node-catalog SoT for schema stubs |

## API key policy

### Current (accepted for development builds)

- Default: **do not** persist the key (`Remember API key on disk` off)
- Opt-in remember: plain text under `nodecraft/config/ai_settings.json`
- Documented honestly in README / settings UI

### 1.0 decision (A5 — frozen)

**Primary: local proxy.** Point `apiBaseUrl` at a user-run proxy that holds the provider
key. Leave the client API key empty unless the proxy needs a local token. This is the only
path that keeps long-lived provider secrets out of the Minecraft process (shared-mod JVM,
heap dumps, other mods).

**Secondary: environment variables** (`NODECRAFT_AI_API_KEY` / `OPENAI_API_KEY` /
`ANTHROPIC_API_KEY`) when calling a provider directly.

**Development only:** session paste + opt-in plaintext remember-on-disk. UI labels these
as dev-only and warns loudly.

**Deferred: OS credential store.** Useful for at-rest secrecy, but it still loads the
provider key into the game process. Not chosen as the 1.0 primary path for this Fabric
client. May revisit later only as an at-rest upgrade for the remember path if still needed.

Named boundary: `AiCredentialPolicy` (source detection, UI guidance, summary labels).
`AiSettingsStore.validate` and `AiRemotePlannerService` allow empty keys when the base URL
looks like a local proxy; auth headers are omitted when the key is blank.

## Non-goals (this freeze)

- Rewriting mock plan templates wholesale
- Changing prompt product copy
- Shipping OS credential store as the 1.0 primary path (deferred; local proxy wins)
- Merging AI into `nodesystem` packages

## Suggested slices

| Slice | Deliverable | Status |
|-------|-------------|--------|
| **A0** | This architecture freeze | **done** |
| **A1** | Extract `AiAssistantController` — panel becomes view | **done** |
| **A2** | Introduce `AiPlanningSession` for remote/mock in-flight state | **done** |
| **A3** | `AiPlannerService` façade over mock + remote | **done** |
| **A4** | `AiPlanValidator` boundary + wire before apply | **done** |
| **A5** | 1.0 credential store **or** local-proxy guidance (product choice) | **done** |

### A1 notes

- `AiAssistantController` owns settings persistence, remote/mock submit, poll, apply/undo/dry-run, session flush
- `AiAssistantUiBindings` holds ImGui widgets + topology preview UI state (same pattern as `PropertyEditSession`)
- `AiAssistantPanel` (~300 lines) is view-only: render + gesture → controller
- Tests: `AiAssistantControllerTest` (no ImGui render)

### A2 notes

- `AiPlanningSession` owns in-flight planning state: last prompt, plan status, pending plan (via component), DSL repair / graph-expansion counters, world-context snapshot, apply/undo bookkeeping
- `AiAssistantController` keeps settings I/O, connection-test future, provider autofill label, and wires UI → session
- Tests: `AiPlanningSessionTest`

### A3 notes

- `AiPlannerService` is the plan-production façade: `planLocal` (template/mock → DSL) + remote prepare/repair/expansion helpers
- Wraps `AiRemotePlanningOrchestrator`; controller no longer holds the orchestrator directly
- Async remote HTTP still submitted via `AiAssistantComponent` (sockets stay out of the façade)
- Tests: `AiPlannerServiceTest`

### A4 notes

- `AiPlanValidator` is the named gate: `parseModelResponse` / `parseAndValidateJson` (DSL) + `checkBeforeApply` / `checkBeforeDryRun`
- Delegates to `AiGraphDslSupport`; does not mutate the canvas
- Controller apply/dry-run/DSL parse / inferred-connection trials go through the validator
- Tests: `AiPlanValidatorTest`

### A5 notes

- Product choice: **local proxy is the 1.0 primary path** (not OS credential store)
- `AiCredentialPolicy` names the policy: proxy / env / session / disk-plaintext / missing
- Settings UI + README push proxy first; remember-on-disk labeled as development only
- Validate + remote planner allow empty API key when base URL looks like a local proxy; omit auth headers when blank
- OS credential store deferred (does not keep secrets out of a shared-mod JVM)
- Tests: `AiCredentialPolicyTest`

Exit for A1: `AiAssistantPanel` no longer calls remote planner / apply / settings persistence directly;
tests cover controller without ImGui (`AiAssistantControllerTest`).

## Related

- `docs/architecture/imgui-node-editor-breakup.md` (same “UI must not own the system” pattern)
- `docs/architecture/property-panel-breakup.md`
- `docs/architecture/node-catalog.md` (AI schema stub U2)
- `README.md` (current API key disclosure)
