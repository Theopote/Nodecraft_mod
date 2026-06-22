# Execution Flow Control (P0)

## Review conclusion (2026-06-21)

The comprehensive review is **directionally correct**: NodeCraft is still primarily a **dataflow** engine. Flow nodes exist, but they route **values**, not **execution**.

| Review claim | Actual state |
|--------------|--------------|
| No exec port type | **Fixed (P0-A slice 1)** — `NodeDataType.EXEC` |
| Static topo only | **Still default** for graphs without exec wires |
| Branch/While cannot skip branches | **Still true for dataflow nodes**; exec scheduler is the path to fix this |
| Cycles fail immediately | **Data cycles still fail**; exec cycles are bounded by `ExecutionRunGuard` |

Existing flow nodes (`flow.control.branch`, `flow.control.sequence`, `flow.loop.*`) remain **dataflow helpers**. Their `@NodeInfo` descriptions already say Branch does not skip downstream nodes.

Reference roadmap: `docs/node-system-完善版路线图-2026-04-26.md` (P0-A / P0-B).

---

## Architecture: two scheduling modes

### 1. Dataflow mode (default)

Used when the graph has **no** `exec` port connections.

- `GraphExecutionPlanner` topo-sorts **data edges only**
- Each node runs **at most once**
- `flow.control.branch` clears the unselected output to `null`, but **both downstream nodes still run**

### 2. Exec-flow mode

Activated automatically when at least one `exec → exec` connection exists.

- **Entry nodes**: nodes with no incoming exec wires
- **Frontier queue**: after a node completes, enqueue its exec successors
- **Lazy data pull**: before running a node, recursively evaluate data upstream on demand
- Nodes outside the exec frontier are **not executed** unless pulled as data dependencies
- **Runaway protection**: `ExecutionRunGuard` enforces `maxSteps` + `maxDurationMs`

---

## Implemented in P0-A (slice 1)

| Component | Role |
|-----------|------|
| `NodeDataType.EXEC` | Execution-control port type |
| `TypeConversionRegistry` | `exec` connects only to `exec` |
| `ExecutionPortKind` | Classifies data vs exec connections |
| `ExecutionFlowGraph` | Entry nodes + exec adjacency **per source port** |
| `ExecutionRunGuard` / `ExecutionRunLimits` | Step/time circuit breaker |
| `NodeExecutor` | Chooses dataflow vs exec-flow; guard in both modes |
| `GraphExecutionPlanner` / dirty propagation | Ignore exec edges for data topo |

## Implemented in P0-A (slice 2)

| Component | Role |
|-----------|------|
| `ExecRoutingNode` / `ExecRouting` | Resolve fired exec output ports after compute |
| `BranchNode` exec ports | `exec_in`, `exec_true`, `exec_false` + conditional routing |
| `NodeExecutor` exec frontier | Enqueues only targets of **fired** exec ports |

Legacy data outputs (`output_true` / `output_false`) remain for dataflow graphs without exec wires.

---

## Implemented in P0-A (slice 3)

| Component | Role |
|-----------|------|
| `ExecRoutingNode.drainExecPortsSequentially()` | Sequence drains each step subtree before firing the next |
| `SequenceNode` exec ports | `exec_in`, `exec_step_1..8` with ordered sequential routing |
| `DoOnceNode` exec ports | `exec_in`, `exec_out`, `exec_blocked` gate routing |
| `NodeExecutor.drainExecFrontier()` | Recursive frontier drain for sequential exec fan-out |

Legacy data outputs on Sequence (`output_step_N`) and DoOnce (`output_first_pass` / `output_blocked`) remain for dataflow graphs without exec wires.

---

## Implemented in P0-B (slice 1)

| Component | Role |
|-----------|------|
| `ExecLoopNode` | ForEach-style repeated body drain with per-iteration output refresh |
| `ForEachLoopNode` exec ports | `exec_in`, `exec_body`, `exec_complete` + `output_item` / `output_index` |
| `WhileLoopNode` exec ports | `exec_in`, `exec_body`, `exec_complete` with condition-based routing |
| `NodeExecutor.drainExecLoop()` | Resets body subtree visit state between iterations |
| Editor exec styling | White exec ports and brighter exec wires in `ConnectionRenderer` / `ImGuiNodeRenderer` |

Legacy list outputs on loop nodes remain for dataflow graphs without exec wires.

---

## Not done yet (P0-B remainder)

1. **Live exec highlight during preview run** — animate active exec frontier in editor
2. **Partial exec + exec mode** — reconcile incremental cache with repeated exec visits

Recommended patterns today:

- `flow.control.branch` with **exec_true/exec_false** for conditional side effects
- `flow.control.sequence` with **exec_step_N** for ordered side-effect chains
- `flow.control.do_once` with **exec_out/exec_blocked** for once-per-run gates
- `flow.loop.for_each` with **exec_body** for per-item side effects
- `flow.loop.while` with **exec_body** loop-back to **exec_in** for conditional loops
- `math.logic.if` for **value** selection in dataflow-only graphs

---

## Usage notes

### Connecting exec ports

Only `exec → exec` is allowed. Data ports cannot mix with exec ports on the same edge.

Flow-control `exec_in` ports accept **multiple incoming exec wires** so paths can merge (e.g. loop-back into `DoOnce`).

### Guard defaults

```java
ExecutionRunLimits.defaults()
// maxSteps = 100_000, maxDurationMs = 30_000
```

Pass custom limits to `NodeExecutor`:

```java
new NodeExecutor(graph, context, null, IncrementalExecutionOptions.defaults(), new ExecutionRunLimits(5L, 1_000L));
```

### Tests

- `ExecFlowExecutorTest` — skip off-frontier nodes, lazy data pull, branch/sequence/do-once/for-each/while exec routing, cycle guard
- `ExecutionFlowGraphTest` — topology analysis
- `FlowControlNodeTest.branchDoesNotSkipEitherDownstreamNodeInExecutor` — documents dataflow limitation

---

## Migration strategy

1. **Existing graphs**: unchanged (no exec wires → dataflow mode)
2. **New control-heavy graphs**: add exec wires incrementally; start from `output.execute.*` entry nodes
3. **Flow node upgrade**: add optional exec outputs alongside legacy data outputs; deprecate data-only routing later
