# Node Language v1 — Organization & Subgraph

**Status: PASSED / FROZEN** (Graph **V58**)

Graph composition under `utilities.organization` (3 runtime nodes). Comment and Group are
**editor metadata** on `SavedGraph`, not catalog nodes.

Related: [`nodecraft-v1-node-language.md`](./nodecraft-v1-node-language.md),
[`contracts/preview-side-effects.md`](./contracts/preview-side-effects.md).

## Product boundary

```text
runtime nodes = Graph Input + Graph Output + Subgraph (order 0–2)
subgraphDefinitions[ref] = graph-local SavedGraph assets (no embeddedGraphJson)
SubgraphCallFrame stack for IO isolation (containsKey semantics)
typed dynamic Subgraph ports from child Graph Input/Output schema
nested NodeExecutor inherits preview skipOutputExecuteSideEffects
MAX_SUBGRAPH_CALL_DEPTH hard budget (GenerationLimits)
duplicate Graph Input/Output names → invalid interface
```

## Inventory (3)

| order | Display name | Type id | Effect |
|------:|--------------|---------|--------|
| 0 | Graph Input | `utilities.organization.graph_input` | `CONTEXT_READ` |
| 1 | Graph Output | `utilities.organization.graph_output` | `CONTEXT_WRITE` |
| 2 | Subgraph | `utilities.organization.subgraph` | `COMPOSITE` |

## Removed from catalog (V58)

- `utilities.organization.subgraph_register`
- `utilities.organization.preset`
- `utilities.organization.comment` → `SavedGraph.comments[]`
- `utilities.organization.group` → `SavedGraph.groups[]`

## Graph Input

- Ports: `Default T` (passthrough `T`), outputs `Value T`, `Name`, `Was Provided`, `Valid`, `Error`
- No `Override` port
- Caller existence: `frame.inputs.containsKey(name)` (null caller values are valid)
- Required + missing key + no default → `Valid=false`

## Graph Output

- Port: `Value T` in/out (passthrough `T`)
- Publishes to `SubgraphCallFrame.outputs` only
- No `Outputs ANY`, no name override port

## Subgraph

- Property: `Subgraph Ref` → key in `SavedGraph.subgraphDefinitions`
- Typed dynamic inputs/outputs from child interface scanner
- Optional `Enabled` port; `Valid` + `Error` outputs
- Disabled → typed outputs null, `Valid=true`
- Depth/recursion guarded by call stack + `GenerationLimits.MAX_SUBGRAPH_CALL_DEPTH`

## SavedGraph schema (V58)

```text
subgraphDefinitions: Map<String, SavedGraph>
comments: SavedGraphComment[]
groups: SavedGraphGroup[]
```

Migration `V57→V58` lifts `embeddedGraphJson`, Comment/Group nodes, and drops obsolete port wires.

## Contract suite

`OrganizationLanguageContractTest` — inventory, effects, typing, preview inheritance, depth cap,
duplicate interface rejection, migration.
