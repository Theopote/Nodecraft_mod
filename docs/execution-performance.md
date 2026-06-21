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

## Incremental execution (4.2)

Partial re-execution avoids recomputing the whole graph when only part of it changed.

| Component | Location | Role |
|-----------|----------|------|
| Invalidation scope | `IncrementalExecutionPlanner#resolveInvalidationScope` | Changed node + all downstream dependents |
| Dirty merge (editor) | `ImGuiNodeEditor#handleNodeDirty` | Unions scopes across debounced dirty events |
| Execution cache | `NodeGraph#getExecutionCache` / `NodeExecutionCache` | Stores last successful `dirtyVersion` per node |
| Preview skip | `IncrementalExecutionOptions.previewDefaults()` | Skips clean cached nodes still inside partial scope |

### How a partial preview run works

1. A node parameter changes → `BaseNode#markDirty()` bumps `dirtyVersion`.
2. The editor merges `resolveInvalidationScope(graph, nodeId)` into `invalidatedNodeIds`.
3. After debounce, `NodeExecutor` runs with that scope and `previewDefaults()`.
4. Nodes **outside** the scope reuse their in-memory outputs (existing behaviour).
5. Nodes **inside** the scope with a valid cache and no recomputed upstream-in-scope neighbour are skipped.
6. After a successful run, `NodeExecutionCache#record` stores each executed node's version.

Graph topology changes (`connect`, `removeNode`, etc.) clear both the editor invalidation set and `NodeExecutionCache`.

Manual full runs (`MenuBarRenderer`, tests) use `IncrementalExecutionOptions.defaults()` — no cache skip inside partial scope.

## Geometry computation (4.1.3)

| Area | Location | Status |
|------|----------|--------|
| Primitive voxelizers | `GeometryVoxelizer` per-shape methods | Analytic generators for boxes, cylinders, booleans, etc. |
| SDF brute-force cap | `GeometryVoxelizer.MAX_SDF_VOXEL_VOLUME` (262 144 blocks) | Skips oversized bounds with a warning |
| Preview mesh quality | `GeometrySurfaceElement` | `particleDensity` clamped 8–64; `enableLOD` caps quality for lighter meshes |
| Preview distance culling | `GhostBlockElement`, `GeometrySurfaceElement` | Uses `PreviewRenderSettings.maxRenderDistance` |

**Deferred** (high cost / platform-specific):

- GPU compute-shader voxelization or SDF sampling
- Camera-distance mesh LOD rebuild (would require re-tessellation on camera move)
- Automatic geometry simplification LOD for bake output

Bake-time voxelization remains CPU-bound; prefer tighter SDF bounds, smaller regions, and shell mode when only surfaces matter.

## Preview memory and render budget (4.2)

| Guard | Location | Role |
|-------|----------|------|
| Block preview sampling | `PreviewSampling` + `GeometryViewerNode` | Down-samples ghost previews (`draftPreviewBlocks` / `maxPreviewBlocks`) |
| Per-frame render budget | `PreviewRenderSettings.maxElementsPerFrame` | Caps how many preview elements draw each frame |
| Active preview cap | `PreviewRenderSettings.maxActivePreviews` | Evicts oldest preview when registration exceeds limit |
| Empty-input grace | `PreviewManager.EMPTY_INPUT_GRACE_MS` | Avoids flicker when inputs briefly go empty |

**Deferred**: execution-time node pooling (see node lifecycle section below).

## Node instance lifecycle (4.2)

The review note *"each execution creates new node instances"* does **not** match the current engine.

| Phase | Behaviour |
|-------|-----------|
| Graph execution | `NodeExecutor` reuses the `INode` instances already stored in `NodeGraph` |
| Per-run state | `BaseNode` clears inputs/outputs inside `compute()`; outputs may persist for partial re-execution |
| New instances | `NodeRegistry.createNodeInstance()` when adding nodes, loading graphs, paste, or introspection |

**Why not pool/singleton graph nodes?**

- Every graph node needs a stable UUID, editor state, and port ownership (`BasePort` → `this`).
- Runtime I/O lives on the instance (`inputValues`, `outputValues`, dirty flags).
- Parallel execution would race on shared singletons.

**What is cached instead**

| Cache | Location | Purpose |
|-------|----------|---------|
| Default parameter snapshots | `NodeRegistry#getDefaultNodeState` | AI/editor defaults without repeated construction per request |
| AI port/param schema | `AiNodeSchemaCatalog.collectAll` | One-time ~521-node introspection per registry epoch |

Graph nodes are long-lived; optimize metadata introspection, not execution pooling.

## Related backlog

- Execution-flow control (exec ports, loops) unlocks more graph semantics than raw perf tuning
- Geometry kernel micro-optimizations beyond bounds caps (parallel SDF, sparse grids)
