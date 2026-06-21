# Execution Performance

## Current model

`NodeExecutor` runs a DAG in **serial topological order**. Each node executes at most once per run.

The `ExecutorService` inside `NodeExecutor` is **not** a parallel node pool. It only moves `executeAsync()` onto one background worker thread so the UI thread does not block.

## What already exists

| Feature | Location | Notes |
|---------|----------|-------|
| Topological schedule | `GraphExecutionPlanner` | Detects cycles, returns execution order |
| Parallel-ready levels | `GraphExecutionPlanner.ExecutionPlan#levels()` | Nodes in the same level have no port dependency between them |
| Partial re-execution | `NodeExecutor(..., executionScopeNodeIds)` | Recomputes scoped nodes only |
| Per-node timing | `ExecutionProfiler` / `NodeExecutor#getLastExecutionProfile()` | Debug summary of slowest nodes |

## Why full parallel execution is deferred

Parallel levels are computed, but `NodeExecutor` still runs them serially because:

1. **Minecraft server thread** – `world.*`, `output.execute.*`, and preview nodes must marshal onto the server tick thread.
2. **Shared mutable nodes** – `BaseNode` stores inputs/outputs on the instance; concurrent `compute()` on the same graph would race.
3. **Implicit ordering** – variable nodes and side-effect nodes create dependencies that are not encoded as port edges.
4. **Preview / world mutation** – not designed for concurrent writers.

## When parallel execution becomes worthwhile

Prerequisites before turning levels into a thread pool:

- Thread-safety policy per node category (pure geometry vs world IO)
- Immutable per-run output buffers or per-node locks
- Variable / side-effect ordering model (or mark nodes as parallel-safe)
- Server-thread batching for world nodes instead of per-node hops

## Profiling during development

Enable debug logging for `com.nodecraft.nodesystem.execution.NodeExecutor` to see:

- level count and `maxParallelWidth`
- `profile=executed=…, totalMs=…, slowest=[…]`

Use `executor.getLastExecutionProfile()` from tests or editor tooling for programmatic inspection.

## Related backlog (review 4.1 / 4.2)

- Incremental execution cache and smarter dirty propagation (partial scope is only the first step)
- Geometry kernel optimizations (voxelizer, SDF sampling) usually dominate graph overhead
