# Flow Loop — Node Language v1

**Status: PASSED / FROZEN** (Graph **V66**)

Flow Loop v1 freezes the language for the 2 `flow.loop` nodes as pure exec repetition.
List aggregation / Take-While / First-Last belong in `math.list`, not here.

## Inventory (order 0–1)

| Order | Display | Id | Effect |
|------:|---------|-----|--------|
| 0 | For Each Loop | `flow.loop.for_each` | `PURE` (`ExecLoopNode`) |
| 1 | While Loop | `flow.loop.while` | `CONTEXT_WRITE` (`ExecRoutingNode` + loop-back; run-local iteration on `ExecutionRunGuard`) |

**Removed:** `flow.loop.accumulator` (use typed `math.list.*`; string join → `math.list.join_strings`).

## For Each

| Port | Type |
|------|------|
| Exec In | EXEC |
| List | LIST&lt;T&gt; (`bindListType`) |
| Enabled | BOOLEAN optional |
| Exec Body / Exec Complete | EXEC |
| Item | T (`bindListElementType`) |
| Index | INTEGER |
| Iteration Count (`output_count`) | INTEGER — planned body pulses for this invocation |
| Valid / Error | BOOLEAN / STRING |

Rules:

- Enabled: unconnected → property default `true`; connected null/wrong type → `Valid=false`, no Complete
- List size `> MAX_LOOP_ITERATIONS` (100_000) → `Valid=false`, Body 0, no Complete
- Empty list / Enabled=false → `Valid=true`, Body 0, Complete once, Iteration Count=0
- Null list elements count as iterations
- Invalid input: `shouldFireExecComplete()=false` (executor does not fire Complete)
- Iteration Count is the **resolved/planned** size, not a completed-body counter after cancellation

## While

| Port | Type |
|------|------|
| Exec In | EXEC |
| Condition | BOOLEAN **required** |
| Max Iterations | INTEGER exact `1..MAX_LOOP_ITERATIONS` |
| Exec Body / Exec Complete | EXEC |
| Iterations | INTEGER |
| Terminated By Condition | BOOLEAN |
| Hit Limit | BOOLEAN |
| Valid / Error | BOOLEAN / STRING |

Rules:

- Condition: **must be connected** (or supplied as strict BOOLEAN); unconnected → `Valid=false`, neither Body nor Complete. No Default Condition property.
- Max Iterations: exact INTEGER on live path; property setter ignores out-of-range (keeps previous)
- Iteration counter is **run-local** on `ExecutionRunGuard` (not node field / SavedGraph)
- Completing a session (false condition or Hit Limit) **clears** the counter; a later pulse in the **same** execution run starts a new independent session
- Dual budget: node Max Iterations + global `ExecutionRunGuard.maxSteps`
- Hit Limit: Condition still true and Iterations == Max → Complete fires with `HitLimit=true`, `Valid=true`

## ExecLoopNode completion policy

```text
shouldFireExecComplete() == false  → 0 body drains, no Complete
shouldFireExecComplete() == true   → after N body drains, fire Complete
```

## Migration (V65 → V66)

Docs-only / language fence (no runtime GraphFormatVersion remaps; stamp-only format):

- Drop wires to removed For Each ports (Items/Indices/Pairs/First/Last)
- Drop wires to While Values in/out
- Remove `flow.loop.accumulator` nodes and incident connections
- Remove legacy `defaultCondition` While property from saved state (ignored on load)

## Verification

```text
./gradlew.bat test --tests "com.nodecraft.nodesystem.contract.FlowLoopLanguageContractTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.flow.FlowControlNodeTest"
./gradlew.bat test --tests "com.nodecraft.nodesystem.execution.ExecFlowExecutorTest"
```
