# NodeSemanticCatalog & Semantic Composer

Status: **Catalog locked for Composer** (S1–S3 + semantic port keys). AiSemanticComposer is next.

## Principle (locked)

`NodeSemanticCatalog` is a **derived view**, not a fifth knowledge base.

- No `node_semantics.json`
- No parallel edge table
- Prefer missing a capability over a false positive
- Catalog is **static** semantic knowledge — never reads graph / node runtime state

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
| `com.nodecraft.nodesystem.semantic` | Catalog, Descriptor, Edge(+Kind), PortKeys, Capability, Domain, Deriver |
| `com.nodecraft.nodesystem.recommendation` | `NodeRecommendationRules`, `NodeRecommendationRulesLoader` (shared SoT + revision) |
| `com.nodecraft.gui.recommendation` | Scorer, Connector, Overlay, UI — **consumers**, not rules owners |

## Edge tiers

`NodeSemanticEdge.kind`:

| Kind | Source | Typical Composer cost |
|------|--------|------------------------|
| `EXACT` | `sourceNodes` port rules | 1 |
| `CATEGORY` | `sourceCategories` output/input type maps | 3 |
| `TYPE` | global `outputTypes` | 5 |

Dedupe key: `(targetNodeId, sourcePortId)` — best kind then better priority wins; prefer a filled `targetPortId` on ties (category string rules often omit connect ports). Different source ports stay distinct.

APIs:

- `exactDownstream` / `exactUpstream`
- `effectiveDownstream` / `effectiveUpstream` (merged)
- Port-local: `effectiveDownstream(nodeId, portKey, dataType)`

## Semantic port keys

Recommendation rules may use **synthetic** keys such as:

- `output_face:horizontal`
- `output_face:vertical`

while the physical port remains `output_face`.

| Query | Exact synthetic | CATEGORY / TYPE |
|-------|-----------------|-----------------|
| `output_face:horizontal` | horizontal EXACT only | via physical base `output_face` |
| `output_face:vertical` | vertical EXACT only | same |
| `output_face` | **does not** expand to both orientations | physical port only |

Helpers: `NodeSemanticPortKeys.physicalBase` / `isVariant` / `matchesEdgePort` (equality only).

**Boundary:** Suggested Connections / Composer context resolve orientation from runtime (e.g. GetBoxFace face name) and pass the semantic key into Catalog. Catalog never opens a graph or node instance for that purpose.

## Capability derivation

`NodeSemanticDeriver` priority: exact families → category-family → `NodeEffect` → conservative fallback.

- `window_array` → WINDOW + OPENING + ARRAY; other `window*` → WINDOW only
- `door_array` → OPENING + ARRAY; other door ids → OPENING
- `difference` / subtract → BOOLEAN_CUT; union/intersection → not
- `WORLD_APPLY` from `effect == WORLD_WRITE` (not `output.execute.*`)

### WORLD_APPLY vs APPLY (P2)

Both enum values still exist and are co-tagged on `WORLD_WRITE`. Converge or document Composer goal semantics before Composer goals API — not a Catalog blocker.

## Cache / revision

Cache key: `(NodeRegistry.introspectionEpoch, NodeRecommendationRulesLoader.getRulesRevision())`.

## Local Planner path (P2)

```text
Template match → AiSemanticComposer → abstain
```

Do not expand `MockTemplateKind`. Do not rewrite Suggested Connections to consume Catalog yet.

## Non-goals

- No Composer in this Catalog lock round
- No Suggested Connections rewrite
- No APPLY vs WORLD_APPLY enum merge yet
