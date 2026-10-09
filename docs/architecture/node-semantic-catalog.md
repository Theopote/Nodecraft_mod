# NodeSemanticCatalog & Semantic Composer

Status: **P1 hardened (S1–S3)**. AiSemanticComposer remains P2.

## Principle (locked)

`NodeSemanticCatalog` is a **derived view**, not a fifth knowledge base.

- No `node_semantics.json`
- No parallel edge table
- Prefer missing a capability over a false positive

```text
NodeRegistry + NodeInfo + NodeEffect
NodeRecommendationRules (+ Loader revision)
TypeConversionRegistry
        ↓
NodeSemanticCatalog   (read-only facade)
        ↓
AiPlanCapabilityCoverage / AiNodeSchemaCatalog / AiSchemaRetrievalService
        ⇣ (P2)
AiSemanticComposer
```

## Packages

| Package | Contents |
|---------|----------|
| `com.nodecraft.nodesystem.semantic` | Catalog, Descriptor, Edge(+Kind), Capability, Domain, Deriver |
| `com.nodecraft.nodesystem.recommendation` | `NodeRecommendationRules`, `NodeRecommendationRulesLoader` (shared SoT + revision) |
| `com.nodecraft.gui.recommendation` | Scorer, Connector, Overlay, UI — **consumers**, not rules owners |

Catalog must **not** depend on `gui.ai`. Rules POJO/Loader live in nodesystem so semantic core is not tied to GUI.

## Edge tiers

`NodeSemanticEdge.kind`:

| Kind | Source | Typical Composer cost |
|------|--------|------------------------|
| `EXACT` | `sourceNodes` port rules | 1 |
| `CATEGORY` | `sourceCategories` output/input type maps | 3 |
| `TYPE` | global `outputTypes` | 5 |

APIs:

- `exactDownstream` / `exactUpstream`
- `effectiveDownstream` / `effectiveUpstream` (merged; best tier wins on dedupe)
- Port-local overloads: `effectiveDownstream(nodeId, portId, dataType)`

`describe().downstream/upstream` = **effective** merged list. AI schema export caps hints (EXACT first).

## Capability derivation

`NodeSemanticDeriver` priority: exact families → category-family → `NodeEffect` → conservative fallback.

Examples:

- `window_array` → WINDOW + OPENING + ARRAY; other `window*` → WINDOW only
- `door_array` → OPENING + ARRAY; other door ids → OPENING
- `difference` / subtract → BOOLEAN_CUT; union/intersection → not BOOLEAN_CUT
- `WORLD_APPLY` from `effect == WORLD_WRITE` (not `output.execute.*` namespace)

## Cache / revision

Cache key: `(NodeRegistry.introspectionEpoch, NodeRecommendationRulesLoader.getRulesRevision())`.

Loader owns the shared `AtomicLong`. Recommendation service and catalog both bump/read it.

## Local Planner path (P2)

```text
Template match → AiSemanticComposer → abstain
```

Do not expand `MockTemplateKind` meanwhile.

## Non-goals (this harden round)

- No Composer
- No Suggested Connections rewrite
- No APPLY vs WORLD_APPLY enum collapse
