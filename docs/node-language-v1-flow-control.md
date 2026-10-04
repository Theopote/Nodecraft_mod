# Flow Control — Node Language v1

**Status: PASSED / FROZEN** (Graph **V65**)

Flow Control v1 freezes the language boundary for the 3 `flow.control` nodes.
Exec routing is exec-first; Signal is optional typed passthrough and never gates exec.

Executor architecture (EXEC ports, exec frontier, Sequence sequential drain, run guard) is unchanged.

## Inventory (order 0–2)

| Order | Display | Id | Effect |
|------:|---------|-----|--------|
| 0 | Branch | `flow.control.branch` | `PURE` |
| 1 | Sequence | `flow.control.sequence` | `PURE` |
| 2 | Do Once | `flow.control.do_once` | `CONTEXT_WRITE` |

## Shared rules

- Passthrough data ports bind scalar type variable `T` (`bindPassthroughType("T")`)
- All three expose `Valid` / `Error`
- Null Signal is a legal payload; it does **not** suppress exec routing
- Exec In is driven by the NodeExecutor frontier (not read inside `processNode`)

## Branch

| Port | Type |
|------|------|
| Exec In | EXEC |
| Condition | BOOLEAN (strict) |
| Signal | T optional |
| Exec True / Exec False | EXEC |
| True / False | T |
| Valid / Error | BOOLEAN / STRING |

Rules:

- Condition true → Exec True fires; Signal (incl. null) on True
- Condition false → Exec False fires; Signal on False
- Connected null / non-Boolean Condition → `Valid=false`, neither exec fires
- Unconnected Condition → property default (`false`)
- Signal never controls exec routing

## Sequence

| Port | Type |
|------|------|
| Exec In | EXEC |
| Signal | T optional |
| Step Count | INTEGER exact `1..8` |
| Exec Step 1..8 | EXEC |
| Step 1..8 | T |
| Active Step Count | INTEGER |
| Active Step Indexes | INTEGER_LIST |
| Valid / Error | BOOLEAN / STRING |

Rules:

- Step Count exact INTEGER; no `Number.intValue()` truncation; no silent clamp on the live port
- Out of range / invalid → `Valid=false`, no exec steps
- Unconnected → property default `2`
- Property setter `setStepCount`: out-of-range values are **ignored** (keep previous); no silent clamp
- Null Signal still fires active exec steps and replicates null to step outs
- `MAX_STEPS = 8` (fixed ports)

## Do Once

| Port | Type |
|------|------|
| Exec In | EXEC |
| Signal | T optional |
| Reset | EXEC optional |
| Exec Out / Exec Blocked | EXEC |
| First Pass / Blocked | T |
| Did Execute / Has Executed | BOOLEAN |
| Valid / Error | BOOLEAN / STRING |

Rules:

- Gate is **once per execution run** (not once per SavedGraph / session)
- Gate state lives on `ExecutionRunGuard` run-local flags keyed by node id
- **Requires** an `ExecutionRunGuard` (shared on context or thread-local); missing → `Valid=false`, no exec
- Never serialize executed state into `getNodeState()` / SavedGraph
- First Exec In pulse → Exec Out; subsequent pulses in the same run → Exec Blocked
- Reset EXEC pulse clears the current run’s flag; Reset-only activation clears and fires no Out/Blocked
- Unconnected / not-fired Reset → no reset (not an error)
- Null Signal still fires the appropriate exec out

## Migration (V64 → V65)

- Strip `fallbackExecuted` from `flow.control.do_once` saved state (incl. subgraphs)
- Docs-only note: Reset BOOLEAN → EXEC (no runtime GraphFormatVersion remap; stamp-only format)

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.FlowControlLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.flow.FlowControlNodeTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.execution.ExecFlowExecutorTest"
```
